package bd.sammalani.alumni.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import bd.sammalani.alumni.common.error.ApiException;

/**
 * The caller, read from the security context.
 * <p>
 * Services take it from here rather than from a controller argument so that no
 * endpoint can accidentally act on an id supplied in the request body — the
 * classic "update /profile with someone else's id" hole.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthPrincipal> find(TokenKind kind) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return Optional.empty();
        }
        return principal.kind() == kind ? Optional.of(principal) : Optional.empty();
    }

    public static AuthPrincipal member() {
        return find(TokenKind.MEMBER).orElseThrow(() -> ApiException.unauthorized(
                "Please log in to continue.", "চালিয়ে যেতে লগইন করুন।"));
    }

    public static AuthPrincipal admin() {
        return find(TokenKind.ADMIN).orElseThrow(() -> ApiException.unauthorized(
                "Your admin session has ended. Please sign in again.",
                "আপনার অ্যাডমিন সেশন শেষ হয়েছে। আবার লগইন করুন।"));
    }
}
