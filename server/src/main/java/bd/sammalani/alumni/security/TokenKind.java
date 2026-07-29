package bd.sammalani.alumni.security;

/**
 * An admin token is never a member token.
 * <p>
 * They are distinguished by audience inside the signature, not by a claim the
 * caller could edit and not by which endpoint the token arrives at. Being signed
 * in as a member cannot grant admin access even if the same person holds both,
 * and a stolen member token is useless against the admin portal.
 */
public enum TokenKind {
    MEMBER,
    ADMIN,
    /** Exchanged for a new access token; never accepted as one. */
    MEMBER_REFRESH
}
