package bd.sammalani.alumni.domain.person;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.common.audit.AuditTrail;
import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.domain.payment.Payment;
import bd.sammalani.alumni.domain.payment.PaymentRepository;
import bd.sammalani.alumni.domain.payment.PaymentStatus;
import bd.sammalani.alumni.domain.registration.Registration;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.CoordinatorDto;
import bd.sammalani.alumni.domain.registration.RegistrationRepository;
import bd.sammalani.alumni.domain.registration.RegistrationService;
import bd.sammalani.alumni.domain.registration.RegistrationStatus;
import bd.sammalani.alumni.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A member removing their own account.
 * <p>
 * Google Play requires this: any app that lets a user create an account must let
 * them delete it and the data behind it from inside the app. Until it existed,
 * every build of the Android app was rejected regardless of what else changed.
 *
 * <h2>What is erased, and what is not</h2>
 * Everything the member typed in goes: phone — which is the credential itself,
 * so this is what actually ends the account — email, date of birth, gender,
 * blood group, occupation, city, the photo (deleted from object storage, not
 * merely unlinked) and the {@code extras} bag. The person row is then tombstoned,
 * which removes it from every query in the application: {@code @SoftDelete} adds
 * {@code deleted_at is null} to all of them with no way to opt out.
 * <p>
 * Name and batch year stay on the hidden row. They were typed in off a school
 * register by a volunteer, they predate the account, and keeping them is what
 * lets an admin restore the person if they come back — which the note on
 * {@link Person} says they will. Nothing can read them in the meantime.
 *
 * <h2>Why the order below is the order</h2>
 * The scrub is flushed <em>before</em> the tombstone. Hibernate will not flush
 * pending updates to an entity that has been passed to {@code remove}, and with
 * {@code @SoftDelete} the removal is itself just an {@code UPDATE} of one column
 * — so scrubbing and deleting in the natural order would write the tombstone and
 * silently discard every cleared field. {@code AccountDeletionIT} pins this.
 *
 * <h2>Money</h2>
 * Payments are never deleted, only flagged {@code refundPending}; see the note
 * on {@link Payment} and {@code V7__account_deletion.sql}. The registration is
 * cancelled and tombstoned, which it would effectively be anyway — its
 * {@code person} association is {@code optional = false}, so once the person is
 * deleted the inner join stops matching and the row leaves every query.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountDeletionService {

    private final PersonRepository people;
    private final RegistrationRepository registrations;
    private final RegistrationService registrationService;
    private final PaymentRepository payments;
    private final StorageService storage;
    private final AuditTrail trail;

    @PersistenceContext
    private EntityManager em;

    /**
     * What the member is about to lose, so the app can say so before asking them
     * to confirm rather than after.
     *
     * @param amountPaid  confirmed money only — a payment the coordinator has not
     *                    yet accepted is not something to promise back
     * @param coordinators who to call about a refund, because deleting the phone
     *                    number is precisely what stops anyone calling them
     */
    public record DeletionPreview(
            boolean hasRegistration,
            RegistrationStatus registrationStatus,
            BigDecimal amountPaid,
            boolean refundPending,
            List<CoordinatorDto> coordinators) {
    }

    @Transactional(readOnly = true)
    public DeletionPreview preview(UUID personId) {
        Person person = require(personId);
        Optional<Registration> registration = registrationService.findMine(personId);
        BigDecimal paid = confirmedTotal(personId);
        boolean refundable = paid.signum() > 0;

        return new DeletionPreview(
                registration.isPresent(),
                registration.map(Registration::getStatus).orElse(null),
                paid,
                refundable,
                refundable && person.getBatchYear() != null
                        ? registrationService.coordinatorsFor(person.getBatchYear())
                        : List.of());
    }

    @Transactional
    public void delete(UUID personId) {
        Person person = require(personId);

        trail.note("Member deleted their own account");

        // One pass, and every row it loads leaves the persistence context by the
        // end of it.
        //
        // Payment.person points at the row this method is about to tombstone, and
        // Hibernate reads a soft-deleted target as transient. Every managed entity
        // is walked again when the transaction commits — being clean is not enough
        // to be skipped — so any payment still attached at that point fails the
        // whole delete with "references an unsaved transient instance". Flushing
        // writes the flag while the person is still live; detaching takes the row
        // out of the context so the commit never looks at it again. That applies to
        // the rows this does *not* flag as much as the ones it does, which is why
        // the detach is outside the branch. AccountDeletionIT pins all of it.
        BigDecimal paid = BigDecimal.ZERO;
        for (Payment payment : payments.findByPersonId(personId)) {
            if (payment.getStatus() == PaymentStatus.CONFIRMED) {
                paid = paid.add(payment.getAmountBdt());
                payment.setRefundPending(true);
                payments.saveAndFlush(payment);
            }
            em.detach(payment);
        }

        registrationService.findMine(personId).ifPresent(registration -> {
            registration.setStatus(RegistrationStatus.CANCELLED);
            // Guest names are the other people's data on this row, and they go
            // with it rather than waiting on the tombstone to hide them.
            registration.getGuests().clear();
            registration.setMemberNote(null);
            registrations.saveAndFlush(registration);
            registrations.delete(registration);
        });

        if (person.getPhotoUrl() != null) {
            // Removed from MinIO, not merely unlinked — an object left in the
            // bucket is still the member's face on someone else's disk.
            storage.delete(person.getPhotoUrl());
        }

        scrub(person);
        people.saveAndFlush(person);
        people.delete(person);

        log.info("Person {} deleted their own account (batch {}, refund pending on {})",
                personId, person.getBatchYear(), paid);
    }

    /**
     * Clears every field the member supplied. Name, Bengali name and batch year
     * are deliberately left — see the class note.
     */
    private void scrub(Person person) {
        person.setPhone(null);
        person.setEmail(null);
        person.setDob(null);
        person.setGender(null);
        person.setBloodGroup(null);
        person.setOccupation(null);
        person.setCity(null);
        person.setPhotoUrl(null);
        person.getExtras().clear();
        // Back to how the row arrived: a name on a register that nobody has
        // claimed. claimedAt is the proof they held the number, and the number
        // is gone.
        person.setClaimedAt(null);
        person.setStatus(PersonStatus.SEEDED);
    }

    private BigDecimal confirmedTotal(UUID personId) {
        return payments.findByPersonId(personId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                .map(Payment::getAmountBdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Person require(UUID personId) {
        return people.findById(personId).orElseThrow(() -> ApiException.notFound(
                "Profile not found.", "প্রোফাইল পাওয়া যায়নি।"));
    }
}
