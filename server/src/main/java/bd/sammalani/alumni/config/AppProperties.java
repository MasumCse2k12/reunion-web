package bd.sammalani.alumni.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Every tunable the service has, bound once and validated at startup.
 * <p>
 * Nothing in this application reads a magic number or a secret from a literal
 * in a method body: if it can differ between the laptop, staging and the day of
 * the reunion, it is here and it comes from the environment.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Otp otp,
        Security security,
        Event event,
        Cors cors,
        Bootstrap bootstrap) {

    /**
     * Signing material for both token audiences. The secret must be supplied by
     * the environment — there is no default, so a misconfigured deployment fails
     * at startup rather than signing tokens with a value published on GitHub.
     */
    public record Jwt(
            @NotBlank String secret,
            @DefaultValue("sammalani-alumni") String issuer,
            @DefaultValue("PT12H") Duration memberAccessTtl,
            @DefaultValue("P60D") Duration memberRefreshTtl,
            @DefaultValue("PT8H") Duration adminAccessTtl) {
    }

    /**
     * One-time codes. {@code devCode} short-circuits SMS on a laptop; it is
     * refused unless the {@code dev} profile is active (see OtpService).
     */
    public record Otp(
            @DefaultValue("6") @Positive int length,
            @DefaultValue("PT5M") Duration ttl,
            @DefaultValue("5") @Positive int maxAttempts,
            @DefaultValue("5") @Positive int requestsPerHour,
            String devCode) {
    }

    public record Security(
            @DefaultValue("5") @Positive int maxFailedLogins,
            @DefaultValue("PT15M") Duration lockoutDuration) {
    }

    /** The reunion the service is currently serving. */
    public record Event(
            @DefaultValue("reunion-2027") String defaultSlug,
            @DefaultValue("1968") int firstBatch,
            @DefaultValue("2026") int lastBatch) {
    }

    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }

    /**
     * The first super admin. Created on an empty database only — never on one
     * that already has an admin — so redeploying cannot silently reset access.
     */
    public record Bootstrap(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("superadmin") String username,
            String password,
            @DefaultValue("Super Admin") String name,
            @DefaultValue("") String phone) {
    }
}
