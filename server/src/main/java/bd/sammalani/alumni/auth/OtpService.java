package bd.sammalani.alumni.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-time codes, held in Redis and nowhere else.
 * <p>
 * Redis rather than a table because a challenge is worthless five minutes after
 * it is issued: a TTL expires it without a cleanup job, and nothing about a
 * failed login attempt belongs in a database that will be backed up for twenty
 * years. The code is stored as a SHA-256 digest — an OTP is a password with a
 * short life, and a Redis dump should not hand someone a live one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final String CHALLENGE_KEY = "otp:challenge:";
    private static final String RATE_KEY = "otp:rate:";
    private static final String FIELD_PERSON = "personId";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final StringRedisTemplate redis;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    void warnIfCodeIsFixed() {
        if (props.otp().devCode() != null && !props.otp().devCode().isBlank()) {
            log.warn("app.otp.dev-code is set — every OTP is '{}'. This must never be set in production.",
                    props.otp().devCode());
        }
    }

    /**
     * @return the challenge to quote back, plus the code itself when running with
     *         a fixed dev code so a developer is not blocked on an SMS gateway
     */
    public Challenge issue(UUID personId, String phone) {
        enforceRequestRate(phone);

        String code = fixedCode() != null ? fixedCode() : randomCode();
        String challengeId = UUID.randomUUID().toString();

        redis.opsForHash().putAll(CHALLENGE_KEY + challengeId, Map.of(
                FIELD_PERSON, personId.toString(),
                FIELD_PHONE, phone,
                FIELD_CODE, digest(code),
                FIELD_ATTEMPTS, "0"));
        redis.expire(CHALLENGE_KEY + challengeId, props.otp().ttl());

        // The SMS gateway goes here. Until it exists, the code is logged rather
        // than returned, so that a fixed dev code is the only way to skip SMS.
        log.info("OTP {} issued for {}", challengeId, mask(phone));
        return new Challenge(challengeId, props.otp().ttl(), fixedCode() != null ? code : null);
    }

    /** @return the person the challenge was issued for */
    public Verified verify(String challengeId, String code) {
        String key = CHALLENGE_KEY + challengeId;
        var hash = redis.opsForHash().entries(key);
        if (hash.isEmpty()) {
            throw ApiException.badRequest("otp_expired",
                    "That code has expired. Please ask for a new one.",
                    "কোডটির মেয়াদ শেষ। নতুন কোড নিন।");
        }

        long attempts = redis.opsForHash().increment(key, FIELD_ATTEMPTS, 1);
        if (attempts > props.otp().maxAttempts()) {
            redis.delete(key);
            throw ApiException.tooManyRequests(
                    "Too many wrong codes. Please ask for a new one.",
                    "অনেকবার ভুল কোড দেওয়া হয়েছে। নতুন কোড নিন।");
        }

        String expected = String.valueOf(hash.get(FIELD_CODE));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                digest(code == null ? "" : code.strip()).getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.badRequest("otp_wrong", "That code is not right.", "কোডটি সঠিক নয়।");
        }

        redis.delete(key);
        return new Verified(UUID.fromString(String.valueOf(hash.get(FIELD_PERSON))),
                String.valueOf(hash.get(FIELD_PHONE)));
    }

    /**
     * A cheap ceiling per number per hour. Not a defence against a determined
     * attacker — it is there so a stuck retry loop cannot spend the committee's
     * SMS budget in an afternoon.
     */
    private void enforceRequestRate(String phone) {
        String key = RATE_KEY + phone;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofHours(1));
        }
        if (count != null && count > props.otp().requestsPerHour()) {
            throw ApiException.tooManyRequests(
                    "Too many code requests. Please try again in an hour.",
                    "অনেকবার কোড চাওয়া হয়েছে। এক ঘণ্টা পর আবার চেষ্টা করুন।");
        }
    }

    private String fixedCode() {
        String dev = props.otp().devCode();
        return dev == null || dev.isBlank() ? null : dev;
    }

    private String randomCode() {
        int bound = (int) Math.pow(10, props.otp().length());
        return String.format("%0" + props.otp().length() + "d", random.nextInt(bound));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private static String mask(String phone) {
        return phone.length() < 11 ? "***" : phone.substring(0, 3) + "*****" + phone.substring(8);
    }

    public record Challenge(String challengeId, Duration expiresIn, String devCode) {
    }

    public record Verified(UUID personId, String phone) {
    }
}
