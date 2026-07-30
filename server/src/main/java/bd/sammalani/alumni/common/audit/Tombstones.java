package bd.sammalani.alumni.common.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Reads and reverses tombstones.
 * <p>
 * <strong>Native SQL, and it has to be.</strong> Hibernate's {@code @SoftDelete}
 * adds {@code deleted_at is null} to every JPQL query, criteria query and
 * {@code find} against these entities, with no way to opt out — which is exactly
 * why the rest of the application cannot leak a deleted person no matter who
 * writes the next query. The price of that guarantee is that the one component
 * which legitimately needs to look past it cannot use the ORM at all. Paying it
 * here, in two queries that will not grow, is a far better trade than an opt-out
 * that every future query could forget.
 * <p>
 * The table name is interpolated into the SQL, so read {@link TombstoneKind}
 * before touching either query: it may only ever come from that enum, and the
 * enum is the reason nothing a caller sends can reach this string.
 */
@Repository
public class Tombstones {

    @PersistenceContext
    private EntityManager em;

    /**
     * What is in the bin for one kind, most recently deleted first.
     * <p>
     * Who deleted it and why are not columns on these tables — they are the audit
     * row for the same {@code (entity, entity_id)}. Joining them on here is what
     * makes the bin say "removed by Rafiqul Islam, duplicate of an existing 1996
     * record" instead of showing a bare timestamp, and it is deliberately a
     * lateral join rather than a second query for every row on the page.
     */
    @Transactional(readOnly = true)
    public List<Tombstone> list(TombstoneKind kind, int limit) {
        String sql = """
                select t.id::text   as id,
                       %s           as label,
                       %s           as batch_year,
                       t.deleted_at as deleted_at,
                       a.actor_label as deleted_by,
                       a.note        as note
                from %s t
                  left join lateral (
                      select actor_label, note
                      from audit_log
                      where entity = :entity and entity_id = t.id::text and action = 'DELETE'
                      order by at desc
                      limit 1
                  ) a on true
                where t.deleted_at is not null
                order by t.deleted_at desc
                limit :max
                """.formatted(kind.labelSql(), kind.batchYearSql(), kind.table());

        // Every column's type is named rather than inferred from what the driver
        // hands back, so a timestamptz arrives as an Instant here and not as a
        // java.sql.Timestamp that happens to work until the driver is upgraded.
        List<Object[]> rows = em.unwrap(Session.class)
                .createNativeQuery(sql, Object[].class)
                .addScalar("id", String.class)
                .addScalar("label", String.class)
                .addScalar("batch_year", Integer.class)
                .addScalar("deleted_at", Instant.class)
                .addScalar("deleted_by", String.class)
                .addScalar("note", String.class)
                .setParameter("entity", kind.table())
                .setParameter("max", limit)
                .getResultList();

        return rows.stream()
                .map(row -> new Tombstone(
                        UUID.fromString((String) row[0]),
                        kind,
                        (String) row[1],
                        (Integer) row[2],
                        (Instant) row[3],
                        (String) row[4],
                        (String) row[5]))
                .toList();
    }

    /**
     * Clear the tombstone.
     *
     * @return false when nothing was deleted under that id — which covers both "no
     *         such row" and "already restored", and the caller wants the same
     *         answer for both, because a second click on a restore button is not
     *         an error
     */
    @Transactional
    public boolean restore(TombstoneKind kind, UUID id) {
        String sql = "update %s set deleted_at = null where id = :id and deleted_at is not null"
                .formatted(kind.table());
        return em.createNativeQuery(sql).setParameter("id", id).executeUpdate() == 1;
    }

    /**
     * One row in the bin.
     *
     * @param label     a name where the table has one — nobody recognises a uuid
     * @param deletedBy the actor from the audit trail, not a column on the row
     */
    public record Tombstone(UUID id, TombstoneKind kind, String label, Integer batchYear,
                            Instant deletedAt, String deletedBy, String note) {
    }
}
