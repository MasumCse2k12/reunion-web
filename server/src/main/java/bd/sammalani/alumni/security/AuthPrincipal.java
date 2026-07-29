package bd.sammalani.alumni.security;

import java.util.UUID;

/**
 * Who is calling, as proved by the token and nothing else.
 * <p>
 * Note what is absent: no batch scope and no role beyond the token kind. Those
 * are authority, they change while a session is open, and they are re-read from
 * the database on every request that needs them. A scope baked into a token is a
 * scope that keeps working for eight hours after it was revoked.
 */
public record AuthPrincipal(UUID personId, TokenKind kind, String displayName) {

    public boolean isAdmin() {
        return kind == TokenKind.ADMIN;
    }
}
