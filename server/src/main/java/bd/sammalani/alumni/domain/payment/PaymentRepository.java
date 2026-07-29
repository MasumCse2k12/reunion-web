package bd.sammalani.alumni.domain.payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findFirstByRegistrationIdOrderByReportedAtDesc(UUID registrationId);

    boolean existsByMethodAndReferenceAndStatusNot(PaymentMethod method, String reference, PaymentStatus status);

    /**
     * The latest payment claim for each registration on a page of the queue —
     * one query for the whole page instead of one per row.
     */
    @Query("""
            select p from Payment p
            where p.registration.id in :registrationIds
              and p.reportedAt = (select max(p2.reportedAt) from Payment p2
                                  where p2.registration.id = p.registration.id)
            """)
    List<Payment> findLatestForRegistrations(@Param("registrationIds") Collection<UUID> registrationIds);
}
