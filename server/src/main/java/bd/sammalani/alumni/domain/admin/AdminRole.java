package bd.sammalani.alumni.domain.admin;

/**
 * Two roles, no more. A GROUP_ADMIN is the batch coordinator from the design
 * doc — the same volunteer, promoted from typing names into deciding them.
 */
public enum AdminRole {
    /** All 59 batches, and the only role that may create admins or set passwords. */
    SUPER_ADMIN,
    /** Scoped to assigned batch years, enforced server-side on every request. */
    GROUP_ADMIN
}
