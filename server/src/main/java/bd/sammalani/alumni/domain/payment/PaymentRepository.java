package bd.sammalani.alumni.domain.payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Both entity reads below name every association {@link Payment} has, because
 * all three are EAGER — a soft-deleted target cannot be proxied, see the note on
 * {@code Person}. Naming them keeps a read at one query; leaving them to the
 * eager default turns every row into four.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"registration", "registration.person", "person", "paidTo"})
    Optional<Payment> findFirstByRegistrationIdOrderByReportedAtDesc(UUID registrationId);

    boolean existsByMethodAndReferenceAndStatusNot(PaymentMethod method, String reference, PaymentStatus status);

    /**
     * The latest payment claim for each registration on a page of the queue —
     * one query for the whole page instead of one per row.
     */
    @Query("""
            select p from Payment p
              join fetch p.registration r
              join fetch r.person
              left join fetch p.person
              left join fetch p.paidTo
            where r.id in :registrationIds
              and p.reportedAt = (select max(p2.reportedAt) from Payment p2
                                  where p2.registration.id = r.id)
            """)
    List<Payment> findLatestForRegistrations(@Param("registrationIds") Collection<UUID> registrationIds);
}
