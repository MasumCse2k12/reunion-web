package bd.sammalani.alumni.common.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.hibernate.Session;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the audit trail: one batched insert, on the same connection and inside
 * the same transaction as the changes it describes.
 * <p>
 * That last part is the whole design. An audit row written on a second connection
 * can commit while the change it records rolls back, which produces a log that
 * accuses somebody of something that never happened — and the reverse, a change
 * that commits while its audit row is lost. Both are worse than no audit trail,
 * because both are believed. So rows go through the Hibernate session's own
 * connection, and a failure to write them fails the transaction.
 * <p>
 * <strong>Where the rows are drained from, and why it is not {@code beforeCommit}.</strong>
 * Rows are buffered and written by {@link AuditFlushListener}, which runs at the
 * end of every Hibernate flush. The obvious alternative — a Spring
 * {@code TransactionSynchronization} draining in {@code beforeCommit} — is wrong,
 * and wrong in a way that looks like it works. Spring fires {@code beforeCommit}
 * <em>before</em> handing off to JPA, and Hibernate does its final flush during
 * that handoff, so the entity events for everything the transaction changed had
 * not happened yet. A synchronization registered by the first of those events
 * would be registered after its own drain point had passed. The result was an
 * audit trail containing only the writes made by code that also called
 * {@link #record} by hand — which is to say, the sign-ins — while every profile
 * edit and every approval went unrecorded. The integration test that caught it is
 * {@code SoftDeleteAuditIT}.
 * <p>
 * Buffering rather than inserting one at a time still earns its keep: a bulk
 * approval of forty applications touches forty registrations, forty people and
 * forty reviews, and this writes them in one batch per flush rather than a
 * hundred and twenty round trips. Draining after the flush also keeps these
 * statements from interleaving with Hibernate's own.
 * <p>
 * Rows are inserted with plain JDBC rather than through an entity, so that
 * writing the trail cannot itself produce entity events and recurse.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditTrail {

    private static final String PENDING_KEY = AuditTrail.class.getName() + ".pending";

    private static final String INSERT_SQL = """
            insert into audit_log (at, action, entity, entity_id, batch_year,
                                   actor_id, actor_kind, actor_label, note, changes,
                                   request_id, ip, method, path)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?)
            """;

    /** Shares Boot's Jackson defaults; the payload is only ever strings and nulls. */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;

    /**
     * Record one change. Called by {@link EntityAuditListener} for every row the
     * ORM writes, and by hand for the domain events it cannot see.
     *
     * @param entityId text rather than a uuid, so a composite or natural key is
     *                 still recordable
     */
    public void record(AuditAction action, String entity, String entityId, Integer batchYear,
                       Map<String, AuditChange> changes) {
        Pending pending = pending();
        pending.rows.add(new Row(Instant.now(), action, entity, entityId, batchYear,
                AuditContext.current(), changes == null ? Map.of() : changes));
        if (pending.standalone) {
            writeStandalone(pending.take());
        }
    }

    /** A change with nothing to diff — a refused sign-in, a lockout. */
    public void record(AuditAction action, String entity, String entityId, Integer batchYear) {
        record(action, entity, entityId, batchYear, Map.of());
    }

    /**
     * Explain the rest of this transaction. "Merged duplicate", "member asked to
     * be removed" — the sentence a diff cannot supply, applied to every row this
     * transaction goes on to write that does not carry its own.
     */
    public void note(String note) {
        if (note != null && !note.isBlank()) {
            pending().note = note.strip();
        }
    }

    /* ---------------- the transaction-scoped buffer ---------------- */

    private Pending pending() {
        Object existing = TransactionSynchronizationManager.getResource(PENDING_KEY);
        if (existing instanceof Pending pending) {
            return pending;
        }

        Pending pending = new Pending();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.bindResource(PENDING_KEY, pending);
            TransactionSynchronizationManager.registerSynchronization(new Flusher());
        } else {
            // Nothing in this application writes outside a transaction, so this is
            // a bug elsewhere rather than a case to support. It still gets its row:
            // losing the record is not the right way to report a missing @Transactional.
            pending.standalone = true;
            log.warn("Audit row recorded outside a transaction; it cannot be rolled back with the change it describes");
        }
        return pending;
    }

    /**
     * Write whatever is buffered. Called at the end of every Hibernate flush, and
     * once more before commit for the rows of a transaction that never flushed —
     * a read-only one that still recorded a refused sign-in, for instance.
     * <p>
     * Safe to call when there is nothing to write, which is most of the time.
     */
    void drain() {
        Object existing = TransactionSynchronizationManager.getResource(PENDING_KEY);
        if (!(existing instanceof Pending pending) || pending.rows.isEmpty()) {
            return;
        }

        List<Row> rows = pending.take();
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
        if (em == null) {
            writeStandalone(rows);
            return;
        }
        em.unwrap(Session.class).doWork(connection -> insert(connection, rows, pending.note));
    }

    /**
     * Catches the rows of a transaction that never flushed, and unbinds the buffer
     * so it cannot leak into the next transaction on this thread. The flush
     * listener does the work in every other case.
     */
    private final class Flusher implements TransactionSynchronization {

        @Override
        public void beforeCommit(boolean readOnly) {
            drain();
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_KEY);
        }
    }

    private void writeStandalone(List<Row> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            insert(connection, rows, null);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not write the audit trail", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /* ---------------- the insert ---------------- */

    private void insert(Connection connection, List<Row> rows, String transactionNote) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            for (Row row : rows) {
                AuditActor actor = row.actor();
                statement.setObject(1, OffsetDateTime.ofInstant(row.at(), ZoneOffset.UTC));
                statement.setString(2, row.action().name());
                statement.setString(3, row.entity());
                statement.setString(4, row.entityId());
                statement.setObject(5, row.batchYear(), Types.INTEGER);
                statement.setObject(6, actor.personId(), Types.OTHER);
                statement.setString(7, actor.kind().name());
                statement.setString(8, actor.label());
                statement.setString(9, transactionNote);
                statement.setString(10, json(row.changes()));
                statement.setString(11, actor.requestId());
                statement.setString(12, actor.ip());
                statement.setString(13, actor.method());
                statement.setString(14, actor.path());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * {@code {"status": {"from": "SEEDED", "to": "VERIFIED"}}}. Both sides are
     * already strings by the time they reach here, so nothing in the payload can
     * fail to serialise and the trail cannot be broken by an unmappable value.
     */
    private static String json(Map<String, AuditChange> changes) {
        Map<String, Map<String, String>> payload = new LinkedHashMap<>();
        changes.forEach((field, change) -> {
            Map<String, String> sides = new LinkedHashMap<>();
            sides.put("from", change.from());
            sides.put("to", change.to());
            payload.put(field, sides);
        });
        return MAPPER.writeValueAsString(payload);
    }

    /** One transaction's worth of unwritten rows. */
    private static final class Pending {

        private final List<Row> rows = new ArrayList<>();
        private String note;
        private boolean standalone;

        private List<Row> take() {
            List<Row> taken = List.copyOf(rows);
            rows.clear();
            return taken;
        }
    }

    private record Row(Instant at, AuditAction action, String entity, String entityId, Integer batchYear,
                       AuditActor actor, Map<String, AuditChange> changes) {
    }
}
