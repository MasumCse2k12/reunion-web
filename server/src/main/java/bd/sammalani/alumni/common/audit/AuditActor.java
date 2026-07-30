package bd.sammalani.alumni.common.audit;

import java.util.UUID;

/**
 * Who made a write, and from where.
 * <p>
 * The label is captured at the time of the write and stored as text rather than
 * resolved by joining to {@code person} when the log is read. That is not a
 * denormalisation for speed — it is the point. A coordinator can be renamed,
 * have their access revoked, or be removed from the register entirely, and the
 * row that says they approved forty registrations in March must still say so.
 *
 * @param personId null for an anonymous or system write; a foreign key otherwise
 * @param label    a username, a masked mobile, or {@code system:bootstrap}
 * @param requestId correlates the audit rows of one HTTP call with each other
 */
public record AuditActor(
        UUID personId,
        ActorKind kind,
        String label,
        String requestId,
        String ip,
        String method,
        String path) {

    /**
     * The fallback when nothing is bound: background work, and tests. It is not
     * a guess at an unknown caller — every path that has a caller binds one.
     */
    public static final AuditActor UNATTRIBUTED = system("unattributed");

    public static AuditActor system(String what) {
        return new AuditActor(null, ActorKind.SYSTEM, "system:" + what, null, null, null, null);
    }

    /**
     * The same request, now with a name to it. Sign-in is the one moment an
     * anonymous caller acquires an identity part-way through a request: the
     * password has been checked but no token exists yet, so the filter could not
     * have bound anybody. Keeping the request details and replacing only the
     * identity is what makes the sign-in row show up under "everything this
     * coordinator did" rather than under a stranger's.
     */
    public AuditActor identified(UUID personId, ActorKind kind, String label) {
        return new AuditActor(personId, kind, label, requestId, ip, method, path);
    }
}
