package bd.sammalani.alumni.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Turns a bearer token into an authentication, or into nothing at all.
 * <p>
 * It never rejects: an absent or unreadable token leaves the context empty and
 * the authorization rules decide what that means for the path being called.
 * That keeps "who are you" separate from "may you".
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String ROLE_MEMBER = "ROLE_MEMBER";
    static final String ROLE_ADMIN = "ROLE_ADMIN";

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER.length()).trim();
            TokenKind kind = jwtService.kindOf(token);

            // A refresh token authenticates nothing; it is only ever exchanged.
            if (kind == TokenKind.MEMBER || kind == TokenKind.ADMIN) {
                AuthPrincipal principal = jwtService.verify(token, kind);
                var authority = new SimpleGrantedAuthority(kind == TokenKind.ADMIN ? ROLE_ADMIN : ROLE_MEMBER);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        chain.doFilter(request, response);
    }
}
