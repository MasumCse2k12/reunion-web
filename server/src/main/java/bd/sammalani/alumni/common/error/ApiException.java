package bd.sammalani.alumni.common.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Every failure this service raises deliberately, in one type.
 * <p>
 * Two things are unusual and both are on purpose. Messages are carried in
 * Bangla as well as English, because the people reading them are alumni of a
 * village school in Narail and an English-only error is an error they cannot
 * act on. And there is one class with factories rather than a hierarchy of
 * eight: the status is data, not a type, and subclassing it would buy nothing
 * except eight more files to keep in step.
 */
@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    /** Stable, machine-readable, safe to switch on from a client. */
    private final String code;
    private final String messageBn;

    public ApiException(HttpStatus status, String code, String message, String messageBn) {
        super(message);
        this.status = status;
        this.code = code;
        this.messageBn = messageBn;
    }

    public static ApiException notFound(String what, String whatBn) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", what, whatBn);
    }

    public static ApiException badRequest(String code, String message, String messageBn) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, messageBn);
    }

    public static ApiException unauthorized(String message, String messageBn) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", message, messageBn);
    }

    public static ApiException forbidden(String message, String messageBn) {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", message, messageBn);
    }

    public static ApiException conflict(String code, String message, String messageBn) {
        return new ApiException(HttpStatus.CONFLICT, code, message, messageBn);
    }

    public static ApiException tooManyRequests(String message, String messageBn) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests", message, messageBn);
    }

    /* ---- the handful the domain raises by name, so callers read as prose ---- */

    public static ApiException outsideBatchScope() {
        return forbidden("This batch is outside your assignment.", "এই ব্যাচটি আপনার দায়িত্বের বাইরে।");
    }

    public static ApiException reasonRequired() {
        return badRequest("reason_required", "Write a reason before rejecting.", "বাতিল করার আগে কারণ লিখুন।");
    }
}
