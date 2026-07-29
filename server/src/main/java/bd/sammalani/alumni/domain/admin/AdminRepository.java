package bd.sammalani.alumni.domain.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRepository extends JpaRepository<AdminCredential, UUID> {

    @EntityGraph(attributePaths = "person")
    @Query("select a from AdminCredential a where lower(a.username) = lower(:username)")
    Optional<AdminCredential> findByUsernameIgnoringCase(@Param("username") String username);

    @Query("select count(a) > 0 from AdminCredential a where lower(a.username) = lower(:username)")
    boolean existsByUsernameIgnoringCase(@Param("username") String username);

    @EntityGraph(attributePaths = "person")
    @Query("select a from AdminCredential a order by a.createdAt")
    List<AdminCredential> findAllWithPerson();

    @EntityGraph(attributePaths = "person")
    Optional<AdminCredential> findWithPersonByPersonId(UUID personId);

    /**
     * Who a member of this batch should pay: the coordinators covering that year,
     * plus nobody else. Name and phone only — this is the one place a member sees
     * another person's number.
     */
    @Query("""
            select a from AdminCredential a
              join fetch a.person
            where a.active = true
              and (a.role = bd.sammalani.alumni.domain.admin.AdminRole.SUPER_ADMIN
                   or :batchYear member of a.batches)
            order by a.role desc
            """)
    List<AdminCredential> findCoordinatorsForBatch(@Param("batchYear") int batchYear);
}
