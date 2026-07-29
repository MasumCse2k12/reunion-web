package bd.sammalani.alumni.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.config.AppProperties;
import jakarta.annotation.PostConstruct;

/**
 * Issues and verifies the service's own tokens. HMAC, one secret, no key server:
 * this is a single service and a JWKS endpoint would be ceremony without a
 * second party to serve.
 */
@Service
public class JwtService {

    private static final String CLAIM_NAME = "name";
    private static final int MIN_SECRET_BYTES = 32;

    private final AppProperties props;
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public JwtService(AppProperties props) {
        this.props = props;
        SecretKey key = secretKey(props.jwt().secret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @PostConstruct
    void warnOnWeakSecret() {
        if (props.jwt().secret().getBytes().length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
    }

    public String issueMemberAccess(UUID personId, String displayName) {
        return issue(personId, displayName, TokenKind.MEMBER, props.jwt().memberAccessTtl());
    }

    public String issueMemberRefresh(UUID personId, String displayName) {
        return issue(personId, displayName, TokenKind.MEMBER_REFRESH, props.jwt().memberRefreshTtl());
    }

    public String issueAdminAccess(UUID personId, String displayName) {
        return issue(personId, displayName, TokenKind.ADMIN, props.jwt().adminAccessTtl());
    }

    public Duration memberAccessTtl() {
        return props.jwt().memberAccessTtl();
    }

    private String issue(UUID personId, String displayName, TokenKind kind, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(personId.toString())
                .audience(java.util.List.of(kind.name()))
                .claim(CLAIM_NAME, displayName == null ? "" : displayName)
                // Distinct id per token so an individual one can be revoked later
                // without invalidating every session the holder has.
                .id(UUID.randomUUID().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /**
     * @param expected the audience this call site accepts — an access token
     *                 presented to /auth/refresh is rejected, and so is the reverse
     */
    public AuthPrincipal verify(String token, TokenKind expected) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            throw ApiException.unauthorized("Your session has ended. Please sign in again.",
                    "আপনার সেশন শেষ হয়েছে। আবার লগইন করুন।");
        }
        if (!jwt.getAudience().contains(expected.name())) {
            throw ApiException.unauthorized("Wrong kind of token for this endpoint.",
                    "এই অনুরোধের জন্য ভুল ধরনের টোকেন।");
        }
        return new AuthPrincipal(UUID.fromString(jwt.getSubject()), expected, jwt.getClaimAsString(CLAIM_NAME));
    }

    /** Reads the audience without trusting it, so the filter knows which chain to build. */
    public TokenKind kindOf(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            for (TokenKind kind : TokenKind.values()) {
                if (jwt.getAudience().contains(kind.name())) {
                    return kind;
                }
            }
        } catch (JwtException ignored) {
            // An unreadable token is simply not authenticated; the filter moves on.
        }
        return null;
    }

    private static SecretKey secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(), "HmacSHA256");
    }
}
