package bd.sammalani.alumni.admin;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.common.audit.AuditTrail;
import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Person-level admin operations — deletion (soft) for now.
 * <p>
 * A GROUP_ADMIN may only act on persons whose batch is inside their scope.
 * A SUPER_ADMIN has no such restriction. The same {@link AdminSession#covers}
 * check used in the review queue is applied here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPersonService {

    private final PersonRepository people;
    private final AdminContextService context;
    private final AuditTrail trail;

    /**
     * Soft-deletes a person by setting {@code deleted_at}.
     * <p>
     * Hibernate's {@code @SoftDelete} translates the JPA {@code delete} call
     * into an {@code UPDATE person SET deleted_at = now() WHERE id = ?}, and
     * the {@link bd.sammalani.alumni.common.audit.EntityAuditListener} records
     * a {@code DELETE} audit row automatically. The optional {@code reason} is
     * attached to the transaction note so every audit row written in this
     * unit of work carries the explanation.
     */
    @Transactional
    public void deletePerson(UUID personId, String reason) {
        AdminSession actor = context.current();

        Person person = people.findById(personId).orElseThrow(() -> ApiException.notFound(
                "Person not found.", "এই ব্যক্তিকে খুঁজে পাওয়া যায়নি।"));

        if (!actor.covers(person.getBatchYear())) {
            throw ApiException.forbidden(
                    "This batch is outside your assignment.", "এই ব্যাচটি আপনার দায়িত্বের বাইরে।");
        }

        if (reason != null && !reason.isBlank()) {
            trail.note(reason.strip());
        }

        people.delete(person);
        log.info("Person {} ({}) soft-deleted by {}", personId, person.getName(), actor.username());
    }
}
