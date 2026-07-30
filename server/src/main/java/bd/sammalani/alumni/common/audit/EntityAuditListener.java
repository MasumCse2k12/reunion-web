package bd.sammalani.alumni.common.audit;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import org.springframework.stereotype.Component;

import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;

/**
 * Audits every row the ORM writes, by listening to the flush rather than by
 * being called.
 * <p>
 * This is the point of doing it here instead of in the services. A service-level
 * audit call is a line somebody has to remember to add, and the write path added
 * next month — by someone who has never read this file — is the one that will not
 * have it. There is no version of that which stays complete. A listener on the
 * flush is complete by construction: if a row changed, it was audited.
 * <p>
 * Two things it does not see, and both are covered by hand instead:
 * <ul>
 *   <li><strong>Element collections.</strong> A change to {@code admin_batch_scope}
 *       is a collection event, not an entity update, so a coordinator's batch
 *       assignment would change silently. That is an authority change and the most
 *       security-relevant write in the application, so {@code AdminAccountService}
 *       records it explicitly as {@code SCOPE_CHANGED}.</li>
 *   <li><strong>Bulk JPQL and criteria updates.</strong> Nothing in this codebase
 *       uses them — every write goes through an entity — and this is the reason to
 *       keep it that way.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EntityAuditListener
        implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final AuditTrail trail;

    /** {@code @Table} lookups, which are reflective and never change at runtime. */
    private final Map<Class<?>, String> tableNames = new ConcurrentHashMap<>();

    @Override
    public void onPostInsert(PostInsertEvent event) {
        EntityPersister persister = event.getPersister();
        if (notAudited(persister)) {
            return;
        }
        trail.record(AuditAction.INSERT, table(persister), id(event.getId()), batchYear(event.getEntity()),
                AuditChanges.ofInsert(persister.getPropertyNames(),
                        render(event.getState(), persister, event.getSession())));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        EntityPersister persister = event.getPersister();
        if (notAudited(persister)) {
            return;
        }

        Map<String, AuditChange> changes = AuditChanges.ofUpdate(
                persister.getPropertyNames(),
                render(event.getOldState(), persister, event.getSession()),
                render(event.getState(), persister, event.getSession()),
                event.getDirtyProperties());

        // A save that only moved updated_at is not an event. Recording it would
        // fill the trail with rows that say nothing, which is how a trail stops
        // being read.
        if (changes.isEmpty()) {
            return;
        }
        trail.record(AuditAction.UPDATE, table(persister), id(event.getId()), batchYear(event.getEntity()), changes);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        EntityPersister persister = event.getPersister();
        if (notAudited(persister)) {
            return;
        }
        // Soft delete: Hibernate has issued an update setting deleted_at, and this
        // event is what it fires for it. The full prior state goes into the row,
        // because it is the snapshot a restore will be judged against.
        trail.record(AuditAction.DELETE, table(persister), id(event.getId()), batchYear(event.getEntity()),
                AuditChanges.ofDelete(persister.getPropertyNames(),
                        render(event.getDeletedState(), persister, event.getSession())));
    }

    /* ---------------- reading a row's state safely ---------------- */

    /**
     * Renders one state array to text, and this method is where the trap is.
     * <p>
     * A Hibernate state array holds live objects, not columns: a {@code @ManyToOne}
     * slot holds the associated entity or an uninitialised proxy. Calling
     * {@code toString} on it — which any naive "serialise the row" would do —
     * issues a SELECT from inside a flush. So an association is reduced to the
     * identifier the session already knows, which never touches the database, and
     * a mapped collection is skipped entirely: it has its own events and is a
     * proxy until something asks for it.
     */
    private String[] render(Object[] state, EntityPersister persister, SharedSessionContractImplementor session) {
        Type[] types = persister.getPropertyTypes();
        String[] rendered = new String[types.length];
        if (state == null) {
            return rendered;
        }

        for (int i = 0; i < types.length; i++) {
            Object value = state[i];
            if (value == null || types[i].isCollectionType()) {
                continue;
            }
            if (types[i].isEntityType()) {
                value = session.getContextEntityIdentifier(value);
            }
            rendered[i] = AuditChanges.render(value);
        }
        return rendered;
    }

    private Integer batchYear(Object entity) {
        return entity instanceof AuditBatchScoped scoped ? scoped.auditBatchYear() : null;
    }

    /** The trail cannot be audited by itself, and would recurse if it tried. */
    private boolean notAudited(EntityPersister persister) {
        return AuditLog.class.equals(persister.getMappedClass());
    }

    /**
     * The table name, so a row written today is still legible after the Java
     * package it came from has been renamed twice.
     */
    private String table(EntityPersister persister) {
        return tableNames.computeIfAbsent(persister.getMappedClass(), type -> {
            Table table = type.getAnnotation(Table.class);
            return table != null && !table.name().isBlank()
                    ? table.name()
                    : type.getSimpleName().toLowerCase(Locale.ROOT);
        });
    }

    private String id(Object id) {
        return String.valueOf(id);
    }
}
