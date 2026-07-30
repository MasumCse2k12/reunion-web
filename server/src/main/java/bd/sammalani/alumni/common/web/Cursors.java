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
        return submittedAt == null || id == null ? null : encode(submittedAt, id.toString());
    }

    /**
     * The same cursor over a {@code bigserial} key, for the audit trail — the one
     * table whose sort key is {@code (at, id)} with a long rather than a uuid.
     * Same encoding, same opacity contract, so a client cannot tell the difference
     * and does not have to.
     */
    public static String encodeSeq(Instant at, Long id) {
        return at == null || id == null ? null : encode(at, id.toString());
    }

    private static String encode(Instant at, String id) {
        long micros = at.getEpochSecond() * 1_000_000L + at.getNano() / 1_000L;
        String raw = micros + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Never throws for malformed input — returns {@code null}, meaning the first page. */
    public static Position decode(String cursor) {
        return decode(cursor, (at, id) -> new Position(at, UUID.fromString(id)));
    }

    /** Never throws for malformed input — returns {@code null}, meaning the first page. */
    public static SeqPosition decodeSeq(String cursor) {
        return decode(cursor, (at, id) -> new SeqPosition(at, Long.parseLong(id)));
    }

    private static <P> P decode(String cursor, java.util.function.BiFunction<Instant, String, P> build) {
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
            Instant at = Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                    Math.floorMod(micros, 1_000_000L) * 1_000L);
            return build.apply(at, raw.substring(separator + 1));
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

    /** A keyset position on a {@code bigserial} key. */
    public record SeqPosition(Instant at, long id) {
    }
}
