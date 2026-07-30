package bd.sammalani.alumni.common.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * One page of the trail, newest first, keyset-paged on {@code (at, id)}.
     * <p>
     * Every filter is optional and expressed as {@code :x is null or ...} in one
     * query rather than as a criteria builder, because these seven predicates are
     * the only seven this table will ever be read by: the questions an audit log
     * gets asked are "what happened lately", "what happened to this row", and
     * "what did this person do".
     * <p>
     * {@code allBatches} is the caller's authority and is resolved from their
     * session, never from the request — the same rule as the review queue. A group
     * admin sees rows for their own years; the rows with no batch year at all are
     * the schema-wide ones and belong to a super admin.
     * <p>
     * The three timestamp parameters are cast, and the query fails without it.
     * The driver sends a timestamp with no declared type and lets the server infer
     * one from context; {@code a.at >= :since} gives it that context but a bare
     * {@code :since is null} does not, so Postgres rejects the statement with
     * "could not determine data type of parameter" — whether or not the filter was
     * actually supplied. The cast supplies the type the position cannot.
     */
    @Query("""
            select a from AuditLog a
            where (:entity is null or a.entity = :entity)
              and (:entityId is null or a.entityId = :entityId)
              and (:actorId is null or a.actorId = :actorId)
              and (:action is null or a.action = :action)
              and (:batchYear is null or a.batchYear = :batchYear)
              and (cast(:since as Instant) is null or a.at >= :since)
              and (cast(:until as Instant) is null or a.at <= :until)
              and (:allBatches = true or a.batchYear in :batches)
              and (cast(:cursorAt as Instant) is null
                   or a.at < :cursorAt
                   or (a.at = :cursorAt and a.id < :cursorId))
            order by a.at desc, a.id desc
            """)
    List<AuditLog> findPage(@Param("entity") String entity,
                            @Param("entityId") String entityId,
                            @Param("actorId") UUID actorId,
                            @Param("action") AuditAction action,
                            @Param("batchYear") Integer batchYear,
                            @Param("since") Instant since,
                            @Param("until") Instant until,
                            @Param("allBatches") boolean allBatches,
                            @Param("batches") Collection<Integer> batches,
                            @Param("cursorAt") Instant cursorAt,
                            @Param("cursorId") Long cursorId,
                            Limit limit);
}
