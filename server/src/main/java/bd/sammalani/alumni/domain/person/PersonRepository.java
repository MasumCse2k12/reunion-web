package bd.sammalani.alumni.domain.person;

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
