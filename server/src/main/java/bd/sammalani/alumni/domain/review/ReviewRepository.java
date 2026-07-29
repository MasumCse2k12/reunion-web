package bd.sammalani.alumni.domain.review;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /**
     * The most recent decision for each subject on a page — one query per page,
     * with the decider fetched, so rendering "reviewed by Rafiqul Islam" does not
     * turn into a query per row.
     */
    @Query("""
            select r from Review r
              join fetch r.decidedBy
            where r.subjectType = :type
              and r.subjectId in :subjectIds
              and r.decidedAt = (select max(r2.decidedAt) from Review r2
                                 where r2.subjectType = r.subjectType and r2.subjectId = r.subjectId)
            """)
    List<Review> findLatestForSubjects(@Param("type") ReviewSubjectType type,
                                       @Param("subjectIds") Collection<UUID> subjectIds);
}
