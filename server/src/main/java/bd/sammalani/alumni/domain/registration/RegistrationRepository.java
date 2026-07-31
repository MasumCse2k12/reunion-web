package bd.sammalani.alumni.domain.registration;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository extends JpaRepository<Registration, UUID>, ApplicationQueryRepository {

    @EntityGraph(attributePaths = {"person", "event", "ticketType"})
    Optional<Registration> findByEventSlugAndPersonId(String eventSlug, UUID personId);

    @EntityGraph(attributePaths = {"person"})
    Optional<Registration> findWithDetailsById(UUID id);

    /** The money side of the overview: what has actually been confirmed, and what has not. */
    @Query("""
            select coalesce(sum(p.amountBdt), 0) from Payment p
              join p.registration r
            where p.status = bd.sammalani.alumni.domain.payment.PaymentStatus.CONFIRMED
              and (:allBatches = true or r.batchYear in :batches)
            """)
    java.math.BigDecimal confirmedAmount(@Param("allBatches") boolean allBatches,
                                         @Param("batches") java.util.Collection<Integer> batches);

    @Query("""
            select coalesce(sum(r.amountDue), 0) from Registration r
            where r.status in (bd.sammalani.alumni.domain.registration.RegistrationStatus.SUBMITTED,
                               bd.sammalani.alumni.domain.registration.RegistrationStatus.APPROVED)
              and r.paymentStatus <> bd.sammalani.alumni.domain.payment.PaymentStatus.CONFIRMED
              and (:allBatches = true or r.batchYear in :batches)
            """)
    java.math.BigDecimal outstandingAmount(@Param("allBatches") boolean allBatches,
                                           @Param("batches") java.util.Collection<Integer> batches);

    @Query("""
            select count(r) from Registration r
            where r.status = :status
              and (:allBatches = true or r.batchYear in :batches)
            """)
    long countByStatusInScope(@Param("status") RegistrationStatus status,
                              @Param("allBatches") boolean allBatches,
                              @Param("batches") java.util.Collection<Integer> batches);

    @Query("""
            select count(r) from Registration r
            where r.paymentStatus = :status
              and r.status <> bd.sammalani.alumni.domain.registration.RegistrationStatus.DRAFT
              and (:allBatches = true or r.batchYear in :batches)
            """)
    long countByPaymentStatusInScope(@Param("status") bd.sammalani.alumni.domain.payment.PaymentStatus status,
                                     @Param("allBatches") boolean allBatches,
                                     @Param("batches") java.util.Collection<Integer> batches);

    long countByStatus(RegistrationStatus status);
}
