package bd.sammalani.alumni.domain.person;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    Optional<Person> findByPhone(String phone);

    boolean existsByPhone(String phone);

    /**
     * "Is my name in this list?" — a fuzzy match over both scripts, served by the
     * trigram indexes on name and name_bn.
     */
    @Query("""
            select p from Person p
            where p.batchYear = :batchYear
              and p.mergedIntoId is null
              and (cast(:q as String) is null
                   or lower(p.name) like lower(concat('%', cast(:q as String), '%'))
                   or lower(coalesce(p.nameBn, '')) like lower(concat('%', cast(:q as String), '%')))
            order by p.name
            """)
    List<Person> searchInBatch(@Param("batchYear") int batchYear, @Param("q") String q, Limit limit);

    /**
     * People on a batch nobody has claimed yet — the "still missing" list that
     * asks members to help find their own classmates.
     */
    @Query("""
            select p from Person p
            where p.batchYear = :batchYear
              and p.status = bd.sammalani.alumni.domain.person.PersonStatus.SEEDED
              and p.deceased = false
              and p.mergedIntoId is null
            order by p.name
            """)
    List<Person> findMissingInBatch(@Param("batchYear") int batchYear, Limit limit);

    long countByStatusIn(List<PersonStatus> statuses);

    /**
     * The identity review queue: people who have proved a mobile number and are
     * waiting for a coordinator to say whether they are really of that batch.
     * <p>
     * This exists because the application queue cannot answer the question. That
     * one is a query over registrations, so somebody who claims their name and
     * never registers for the reunion is invisible to every admin screen and
     * stuck in CLAIMED for good — {@code PersonStatus} is deliberately separate
     * from a registration's, and until now only the registration path could move
     * it.
     * <p>
     * {@code allBatches} is the caller's authority rather than a filter: true
     * only for a super admin. A group admin passes their exact years, and an
     * empty set therefore matches nothing — never everything.
     * <p>
     * Every optional parameter is gated on a boolean rather than on
     * {@code :param is null}, and that is not a style choice. Postgres types a
     * bind from how it is used, and a bare {@code $n is null} uses it for
     * nothing — the driver gets "could not determine data type of parameter" and
     * the whole query fails at runtime. Keeping the real comparison in the
     * clause is what gives each bind its type; the flag is what turns the clause
     * off. The same reason {@code :allBatches = true} is written that way.
     */
    @Query("""
            select p from Person p
            where p.status = :status
              and p.mergedIntoId is null
              and (:allBatches = true or p.batchYear in :scope)
              and (:byBatchYear = false or p.batchYear = :batchYear)
              and (cast(:q as String) is null
                   or lower(p.name) like lower(concat('%', cast(:q as String), '%'))
                   or lower(coalesce(p.nameBn, '')) like lower(concat('%', cast(:q as String), '%'))
                   or coalesce(p.phone, '') like concat('%', cast(:q as String), '%'))
              and (:fromCursor = false
                   or p.claimedAt < :cursorAt
                   or (p.claimedAt = :cursorAt and p.id < :cursorId))
            order by p.claimedAt desc, p.id desc
            """)
    List<Person> findClaimQueue(@Param("status") PersonStatus status,
                                @Param("allBatches") boolean allBatches,
                                @Param("scope") Collection<Integer> scope,
                                @Param("byBatchYear") boolean byBatchYear,
                                @Param("batchYear") Integer batchYear,
                                @Param("q") String q,
                                @Param("fromCursor") boolean fromCursor,
                                @Param("cursorAt") Instant cursorAt,
                                @Param("cursorId") UUID cursorId,
                                Limit limit);

    /** Everything matching the same filter, so the page can say "10 / 143". */
    @Query("""
            select count(p) from Person p
            where p.status = :status
              and p.mergedIntoId is null
              and (:allBatches = true or p.batchYear in :scope)
              and (:byBatchYear = false or p.batchYear = :batchYear)
              and (cast(:q as String) is null
                   or lower(p.name) like lower(concat('%', cast(:q as String), '%'))
                   or lower(coalesce(p.nameBn, '')) like lower(concat('%', cast(:q as String), '%'))
                   or coalesce(p.phone, '') like concat('%', cast(:q as String), '%'))
            """)
    long countClaimQueue(@Param("status") PersonStatus status,
                         @Param("allBatches") boolean allBatches,
                         @Param("scope") Collection<Integer> scope,
                         @Param("byBatchYear") boolean byBatchYear,
                         @Param("batchYear") Integer batchYear,
                         @Param("q") String q);

    /**
     * Find an existing unverified row (SEEDED, no phone) for the same name and
     * batch. Used by the self-registration flow to reuse rather than duplicate a
     * row when someone submits the form more than once without completing OTP.
     */
    @Query("""
            select p from Person p
            where lower(p.name) = lower(:name)
              and p.batchYear = :batchYear
              and p.phone is null
              and p.status = bd.sammalani.alumni.domain.person.PersonStatus.SEEDED
              and p.mergedIntoId is null
            """)
    Optional<Person> findUnverifiedByNameAndBatch(@Param("name") String name, @Param("batchYear") int batchYear);
}
