package bd.sammalani.alumni.domain.registration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.config.AppProperties;
import bd.sammalani.alumni.domain.event.Event;
import bd.sammalani.alumni.domain.event.EventService;
import bd.sammalani.alumni.domain.event.TicketType;
import bd.sammalani.alumni.domain.payment.Payment;
import bd.sammalani.alumni.domain.payment.PaymentRepository;
import bd.sammalani.alumni.domain.payment.PaymentStatus;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.GuestInput;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.PaymentReportInput;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.RegistrationInput;
import lombok.RequiredArgsConstructor;

/**
 * A member's own submission: build it, price it, send it for approval, and
 * report what was paid for it offline.
 * <p>
 * Pricing is done here and never trusted from the client. The amount a member's
 * phone thinks they owe is a display detail; the amount the coordinator collects
 * is whatever this method computed from the ticket table.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final String OWN_TICKET_CODE = "ALUMNI";
    private static final String FREE_CHILD_CODE = "CHILD_FREE";
    private static final int FREE_CHILD_MAX_AGE = 5;

    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    private final PersonRepository people;
    private final bd.sammalani.alumni.domain.admin.AdminRepository admins;
    private final EventService events;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public Optional<Registration> findMine(UUID personId) {
        return registrations.findByEventSlugAndPersonId(props.event().defaultSlug(), personId);
    }

    /**
     * Create or replace the member's draft. Guests are replaced wholesale rather
     * than patched: the screen edits a list, and a diff protocol would be more
     * ways to get the price wrong.
     */
    @Transactional
    public Registration saveDraft(UUID personId, RegistrationInput input) {
        Event event = events.current();
        if (!event.acceptsRegistrations()) {
            throw ApiException.badRequest("registration_closed",
                    "Registration is closed.", "নিবন্ধন বন্ধ হয়ে গেছে।");
        }

        Registration registration = findMine(personId).orElseGet(() -> {
            Person person = people.findById(personId).orElseThrow(() -> ApiException.notFound(
                    "Profile not found.", "প্রোফাইল পাওয়া যায়নি।"));
            Registration fresh = new Registration();
            fresh.setEvent(event);
            fresh.setPerson(person);
            fresh.setBatchYear(person.getBatchYear());
            return fresh;
        });

        if (!registration.editableByMember()) {
            throw ApiException.badRequest("already_submitted",
                    "This registration is being reviewed and cannot be changed.",
                    "এই নিবন্ধনটি যাচাই করা হচ্ছে, এখন পরিবর্তন করা যাবে না।");
        }

        Map<String, TicketType> tickets = ticketsByCode(event);
        List<Guest> guests = new ArrayList<>();
        for (GuestInput guest : Optional.ofNullable(input.guests()).orElseGet(List::of)) {
            guests.add(new Guest(
                    guest.id() != null ? guest.id() : UUID.randomUUID(),
                    guest.name().strip(),
                    guest.relation(),
                    guest.age(),
                    ticketCodeFor(guest, tickets),
                    guest.tshirtSize()));
        }

        registration.setGuests(guests);
        registration.setTshirtSize(input.tshirtSize());
        registration.setFoodPref(input.foodPref());
        registration.setMemberNote(input.memberNote());
        registration.setTicketType(tickets.get(OWN_TICKET_CODE));
        registration.setAmountDue(price(guests, tickets));
        // A rejected registration that is edited becomes a draft again, so the
        // member can fix what the coordinator objected to and resubmit.
        registration.setStatus(RegistrationStatus.DRAFT);

        return registrations.save(registration);
    }

    @Transactional
    public Registration submit(UUID personId) {
        Registration registration = findMine(personId).orElseThrow(() -> ApiException.notFound(
                "You have not started a registration yet.", "আপনি এখনো নিবন্ধন শুরু করেননি।"));

        if (!registration.editableByMember()) {
            throw ApiException.badRequest("already_submitted",
                    "This has already been sent for approval.", "এটি ইতিমধ্যে অনুমোদনের জন্য পাঠানো হয়েছে।");
        }

        registration.setStatus(RegistrationStatus.SUBMITTED);
        registration.setSubmittedAt(Instant.now());
        // Copied now so the review queue can filter and page without a join, and
        // so a later correction to the person's batch cannot silently move a
        // submission out of the coordinator who is holding it.
        registration.setBatchYear(registration.getPerson().getBatchYear());
        return registrations.save(registration);
    }

    /**
     * The member says they paid. This records a claim and nothing more — only a
     * coordinator's confirmation asserts that money arrived.
     */
    @Transactional
    public Payment reportPayment(UUID personId, PaymentReportInput input) {
        Registration registration = findMine(personId).orElseThrow(() -> ApiException.notFound(
                "You have not registered yet.", "আপনি এখনো নিবন্ধন করেননি।"));

        if (registration.getStatus() == RegistrationStatus.DRAFT) {
            throw ApiException.badRequest("not_submitted",
                    "Send your registration for approval first.", "আগে নিবন্ধনটি অনুমোদনের জন্য পাঠান।");
        }
        // Nobody should be sending money against a seat that was refused or given
        // up. A rejected registration is editable, so the way back is to correct
        // it and resubmit — not to pay into a queue no coordinator is working.
        if (registration.getStatus() == RegistrationStatus.REJECTED
                || registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw ApiException.badRequest("not_approved",
                    "This registration is not active. Please correct and send it again first.",
                    "এই নিবন্ধনটি সক্রিয় নেই। আগে সংশোধন করে আবার পাঠান।");
        }
        if (registration.getPaymentStatus() == PaymentStatus.CONFIRMED) {
            throw ApiException.badRequest("already_confirmed",
                    "Your payment is already confirmed.", "আপনার পেমেন্ট আগেই নিশ্চিত হয়েছে।");
        }
        // Caught here for a readable message; the partial unique index is what
        // actually guarantees it under concurrency.
        if (payments.existsByMethodAndReferenceAndStatusNot(input.method(), input.reference().strip(),
                PaymentStatus.REJECTED)) {
            throw ApiException.conflict("reference_taken",
                    "That transaction number has already been reported by someone.",
                    "এই লেনদেন নম্বরটি আগেই জমা দেওয়া হয়েছে।");
        }

        Payment payment = new Payment();
        payment.setRegistration(registration);
        payment.setPerson(registration.getPerson());
        payment.setAmountBdt(input.amount());
        payment.setMethod(input.method());
        payment.setReference(input.reference().strip());
        payment.setReportedAt(Instant.now());
        payment.setStatus(PaymentStatus.REPORTED);
        if (input.paidToId() != null) {
            people.findById(input.paidToId()).ifPresent(payment::setPaidTo);
        }
        payments.save(payment);

        registration.setPaymentStatus(PaymentStatus.REPORTED);
        registrations.save(registration);
        return payment;
    }

    /**
     * Who a member of this batch pays. Cached because it is read on every
     * payment screen and changes only when the committee reassigns a batch —
     * at which point the admin service evicts it by name.
     */
    @org.springframework.cache.annotation.Cacheable(
            value = bd.sammalani.alumni.config.CacheConfig.COORDINATORS, key = "#a0")
    @Transactional(readOnly = true)
    public List<bd.sammalani.alumni.domain.registration.RegistrationDtos.CoordinatorDto> coordinatorsFor(int batchYear) {
        return admins.findCoordinatorsForBatch(batchYear).stream()
                .map(a -> new bd.sammalani.alumni.domain.registration.RegistrationDtos.CoordinatorDto(
                        a.getPersonId(), a.getPerson().getName(), a.getPerson().getNameBn(), a.getPerson().getPhone()))
                .toList();
    }

    /* ---------------- pricing ---------------- */

    private Map<String, TicketType> ticketsByCode(Event event) {
        return event.getTicketTypes().stream()
                .collect(java.util.stream.Collectors.toMap(TicketType::getCode, Function.identity()));
    }

    /**
     * Under-fives are free and take no seat. Everyone else is priced by the
     * ticket matching their relation, falling back to the general guest ticket.
     */
    private String ticketCodeFor(GuestInput guest, Map<String, TicketType> tickets) {
        if (guest.relation() == GuestRelation.CHILD
                && guest.age() != null && guest.age() < FREE_CHILD_MAX_AGE
                && tickets.containsKey(FREE_CHILD_CODE)) {
            return FREE_CHILD_CODE;
        }
        return switch (guest.relation()) {
            case SPOUSE -> tickets.containsKey("SPOUSE") ? "SPOUSE" : "GUEST";
            case CHILD -> tickets.containsKey("CHILD") ? "CHILD" : "GUEST";
            default -> "GUEST";
        };
    }

    private BigDecimal price(List<Guest> guests, Map<String, TicketType> tickets) {
        BigDecimal own = amountOf(tickets, OWN_TICKET_CODE);
        BigDecimal total = own;
        for (Guest guest : guests) {
            total = total.add(amountOf(tickets, guest.ticketTypeCode()));
        }
        return total;
    }

    private BigDecimal amountOf(Map<String, TicketType> tickets, String code) {
        TicketType ticket = tickets.get(code);
        return ticket == null ? BigDecimal.ZERO : ticket.getAmountBdt();
    }
}
