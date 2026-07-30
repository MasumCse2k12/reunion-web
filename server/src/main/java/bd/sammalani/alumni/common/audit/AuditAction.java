package bd.sammalani.alumni.common.audit;

/**
 * What an audit row records.
 * <p>
 * The first four are written by the persistence layer for every entity that
 * changes, and nobody has to remember to call anything. The rest are domain
 * events the ORM genuinely cannot see: no row changes when a sign-in is refused,
 * and an element-collection edit is not an entity update. Those are recorded by
 * hand, at the one place each of them happens.
 */
public enum AuditAction {

    /* ---- written automatically, from the Hibernate flush ---- */

    INSERT,
    UPDATE,
    /** A soft delete. There is no hard delete left in the application. */
    DELETE,
    RESTORE,

    /* ---- recorded by hand, where no row changes or no diff would explain it ---- */

    LOGIN,
    /** A refused password. Worth more than a success: this is what an attack looks like. */
    LOGIN_FAILED,
    LOCKOUT,
    PASSWORD_SET,
    /** A coordinator's batch assignment changed — an authority change, not a data one. */
    SCOPE_CHANGED,
    ACCESS_REVOKED,
    ACCESS_RESTORED
}
