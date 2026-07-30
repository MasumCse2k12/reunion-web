package bd.sammalani.alumni.common.audit;

/**
 * The actor for the current unit of work, bound once per request.
 * <p>
 * A {@link ScopedValue} rather than a {@code ThreadLocal}, for the reason set out
 * in docs/03-TECH-STACK.md §3.2: this service runs every request on a virtual
 * thread, and there may be tens of thousands of them. A ThreadLocal here is
 * mutable, needs a disciplined {@code remove()} in a finally block that somebody
 * will eventually omit, and leaks into a pooled carrier thread when they do. A
 * scoped value is immutable and unbinds itself when the scope exits, so the
 * failure mode is "not bound" rather than "bound to the previous caller" — the
 * difference between an unattributed row and a libel.
 * <p>
 * It also means the audit writer reads the actor where it needs it, instead of
 * threading a parameter through twelve method signatures that have no business
 * knowing about auditing.
 */
public final class AuditContext {

    static final ScopedValue<AuditActor> CURRENT = ScopedValue.newInstance();

    private AuditContext() {
    }

    /**
     * The bound actor, or {@link AuditActor#UNATTRIBUTED}. Never null: an audit
     * row that cannot be written because nobody bound an actor is a change that
     * happened with no record of it, which is strictly worse than a row that
     * says "we do not know".
     */
    public static AuditActor current() {
        return CURRENT.orElse(AuditActor.UNATTRIBUTED);
    }

    /** Attribute background work — startup, a scheduled job, an import. */
    public static void runAs(AuditActor actor, Runnable body) {
        ScopedValue.where(CURRENT, actor).run(body);
    }
}
