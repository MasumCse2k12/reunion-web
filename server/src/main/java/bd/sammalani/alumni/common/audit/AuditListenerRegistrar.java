package bd.sammalani.alumni.common.audit;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hooks {@link EntityAuditListener} into Hibernate's flush.
 * <p>
 * Done from a {@code @PostConstruct} against the built session factory rather
 * than through Hibernate's {@code Integrator} SPI, which is discovered by
 * {@code ServiceLoader} and so cannot be given an injected dependency without a
 * static holder to smuggle it through. This registers the actual Spring bean,
 * with its actual collaborators, and fails loudly at startup if it cannot.
 * <p>
 * {@code POST_INSERT} rather than {@code POST_COMMIT_INSERT}: the trail must be
 * written inside the transaction, and a post-commit listener is by definition too
 * late to be part of it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditListenerRegistrar {

    private final EntityManagerFactory entityManagerFactory;
    private final EntityAuditListener listener;
    private final AuditFlushListener flushListener;

    @PostConstruct
    void register() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry registry = sessionFactory.getEventEngine().getListenerRegistry();

        registry.appendListeners(EventType.POST_INSERT, listener);
        registry.appendListeners(EventType.POST_UPDATE, listener);
        registry.appendListeners(EventType.POST_DELETE, listener);

        // Appended, so it runs after the listener that actually performs the flush.
        // Both FLUSH and AUTO_FLUSH: the first is the explicit and commit-time
        // flush, the second is the one Hibernate does before a query whose results
        // pending changes would affect. Buffered rows must be written either way.
        registry.appendListeners(EventType.FLUSH, flushListener);
        registry.appendListeners(EventType.AUTO_FLUSH, flushListener);

        log.info("Audit trail active: every entity insert, update and soft delete is recorded");
    }
}
