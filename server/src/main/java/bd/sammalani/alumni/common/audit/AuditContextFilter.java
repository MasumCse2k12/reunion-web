package bd.sammalani.alumni.common.audit;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import bd.sammalani.alumni.security.AuthPrincipal;
import bd.sammalani.alumni.security.TokenKind;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Binds the caller to {@link AuditContext} for the length of the request.
 * <p>
 * It must sit after the JWT filter, because it reads the authentication that
 * filter established; see where it is added in {@code SecurityConfig}. It is
 * deliberately not a {@code @Component} — a {@code Filter} bean is also
 * auto-registered in the outer servlet chain, where it would run before
 * authentication exists and bind every caller as anonymous.
 * <p>
 * Nothing here rejects anything. An unauthenticated request is a legitimate
 * writer in this application — "my name is not in the list" creates a person
 * before anybody has a session — and it is recorded as such.
 */
public class AuditContextFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    /** Long enough to correlate a day of requests, short enough to read in a log. */
    private static final int REQUEST_ID_LENGTH = 12;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        AuditActor actor = actorOf(request);
        try {
            ScopedValue.where(AuditContext.CURRENT, actor).call(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // ScopedValue.call widens to Throwable; nothing in this chain throws
            // a checked exception that is not one of the three above.
            throw new ServletException(e);
        }
    }

    private AuditActor actorOf(HttpServletRequest request) {
        String requestId = requestId(request);
        String ip = clientIp(request);
        String method = request.getMethod();
        String path = request.getRequestURI();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal
                && principal.kind() != TokenKind.MEMBER_REFRESH) {
            ActorKind kind = principal.kind() == TokenKind.ADMIN ? ActorKind.ADMIN : ActorKind.MEMBER;
            return new AuditActor(principal.personId(), kind, label(principal), requestId, ip, method, path);
        }
        return new AuditActor(null, ActorKind.ANONYMOUS, "anonymous", requestId, ip, method, path);
    }

    /**
     * The display name from the token, which is what a human reading the trail
     * recognises. {@code actor_id} carries the identity; this only has to be
     * legible, so a blank name falls back to the id rather than to nothing.
     */
    private String label(AuthPrincipal principal) {
        String name = principal.displayName();
        return name == null || name.isBlank() ? principal.personId().toString() : name;
    }

    /** Honours an upstream request id so a proxy's logs and these rows line up. */
    private String requestId(HttpServletRequest request) {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        if (supplied != null && !supplied.isBlank()) {
            return supplied.strip().substring(0, Math.min(supplied.strip().length(), 64));
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, REQUEST_ID_LENGTH);
    }

    /**
     * The first hop of {@code X-Forwarded-For}, which is the client as far as our
     * own reverse proxy is aware. Trusted no further than that: it is a header,
     * so it is a claim, and it is recorded as one rather than acted upon.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].strip();
            if (!first.isEmpty()) {
                return first.substring(0, Math.min(first.length(), 45));
            }
        }
        return request.getRemoteAddr();
    }
}
