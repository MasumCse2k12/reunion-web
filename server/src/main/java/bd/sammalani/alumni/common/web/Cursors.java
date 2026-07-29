package bd.sammalani.alumni.common.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import bd.sammalani.alumni.common.error.ApiException;

/**
 * Keyset cursors, encoded as base64url of {@code <epochMicros>:<uuid>}.
 * <p>
 * Keyset rather than offset, because the review queue is read slowly by several
 * coordinators at once while rows are being decided underneath them. An OFFSET
 * would silently skip an applicant every time a row above them left the filter —
 * the one failure mode nobody would notice and everybody would suffer.
 * <p>
 * The encoding is an implementation detail. It is base64 so that no client is
 * tempted to parse it, and any cursor that does not decode is treated as
 * "start from the top" rather than an error: a stale bookmark should not be a
 * 400 in someone's face.
 */
public final class Cursors {

    private static final String SEPARATOR = ":";

    private Cursors() {
    }

    public static String encode(Instant submittedAt, UUID id) {
        if (submittedAt == null || id == null) {
            return null;
        }
        long micros = submittedAt.getEpochSecond() * 1_000_000L + submittedAt.getNano() / 1_000L;
        String raw = micros + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Never throws for malformed input — returns {@code null}, meaning the first page. */
    public static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator <= 0) {
                return null;
            }
            long micros = Long.parseLong(raw.substring(0, separator));
            UUID id = UUID.fromString(raw.substring(separator + 1));
            Instant at = Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                    Math.floorMod(micros, 1_000_000L) * 1_000L);
            return new Position(at, id);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    /** Guards the page size so one client cannot ask for the whole database. */
    public static int clampLimit(Integer requested, int defaultSize, int maxSize) {
        if (requested == null) {
            return defaultSize;
        }
        if (requested < 1) {
            throw ApiException.badRequest("invalid_limit", "limit must be at least 1.",
                    "limit অন্তত ১ হতে হবে।");
        }
        return Math.min(requested, maxSize);
    }

    public record Position(Instant submittedAt, UUID id) {
    }
}
