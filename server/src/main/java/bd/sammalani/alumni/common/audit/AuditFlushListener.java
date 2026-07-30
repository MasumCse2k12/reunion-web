package bd.sammalani.alumni.common.audit;

import org.hibernate.event.spi.AutoFlushEvent;
import org.hibernate.event.spi.AutoFlushEventListener;
import org.hibernate.event.spi.FlushEvent;
import org.hibernate.event.spi.FlushEventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Writes the buffered audit rows at the end of every flush.
 * <p>
 * Appended to Hibernate's {@code FLUSH} listeners rather than replacing them, so
 * it runs <em>after</em> the default listener has done the flushing — which makes
 * it the post-flush hook Hibernate 7 does not otherwise have. By that point every
 * insert, update and delete in the flush has fired its event and buffered its row,
 * the statements are already on the wire, and the transaction is still open. That
 * is the only moment with all three of those properties true at once.
 * <p>
 * Running after rather than during the flush also keeps this insert from
 * interleaving with the statements Hibernate is batching.
 */
@Component
@RequiredArgsConstructor
public class AuditFlushListener implements FlushEventListener, AutoFlushEventListener {

    private final AuditTrail trail;

    @Override
    public void onFlush(FlushEvent event) {
        trail.drain();
    }

    /**
     * The flush Hibernate performs on its own before a query whose results the
     * pending changes would change. It writes the same statements as an explicit
     * flush, so it fires the same entity events, so it buffers the same rows.
     */
    @Override
    public void onAutoFlush(AutoFlushEvent event) {
        trail.drain();
    }
}
