package bd.sammalani.alumni.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Problem+JSON for everything, per RFC 9457.
 * <p>
 * Each response carries {@code code} and {@code messageBn} alongside the
 * standard fields, so a client can branch on the code and show a member the
 * Bangla sentence without a translation table of its own.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail onApiException(ApiException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getMessageBn());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onInvalidBody(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation_failed",
                "Some of the details are not valid.", "কিছু তথ্য সঠিক নয়।");
        problem.setProperty("fields", fields);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail onInvalidParameter(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation_failed", ex.getMessage(), "কিছু তথ্য সঠিক নয়।");
    }

    /**
     * A unique index doing its job — map known constraint names to actionable
     * messages; fall back to a generic one for anything unexpected.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onConflict(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Integrity violation: {}", cause);

        if (cause != null) {
            if (cause.contains("person_phone_uidx")) {
                return problem(HttpStatus.CONFLICT, "phone_taken",
                        "This mobile number is already registered to another account. "
                                + "Go back and search for your name instead.",
                        "এই মোবাইল নম্বরটি অন্য একটি অ্যাকাউন্টে নিবন্ধিত। "
                                + "ফিরে গিয়ে আপনার নাম খুঁজুন।");
            }
            if (cause.contains("admin_username_uidx")) {
                return problem(HttpStatus.CONFLICT, "username_taken",
                        "That username is already in use. Choose a different one.",
                        "এই ব্যবহারকারীর নামটি ইতিমধ্যে ব্যবহার হচ্ছে।");
            }
            if (cause.contains("payment_reference_uidx")) {
                return problem(HttpStatus.CONFLICT, "payment_reference_taken",
                        "This transaction reference has already been submitted. "
                                + "Check your payment history before submitting again.",
                        "এই লেনদেন নম্বরটি ইতিমধ্যে জমা দেওয়া হয়েছে। "
                                + "আবার জমা দেওয়ার আগে আপনার পেমেন্ট ইতিহাস দেখুন।");
            }
            if (cause.contains("registration") && cause.contains("event_id") && cause.contains("person_id")) {
                return problem(HttpStatus.CONFLICT, "already_registered",
                        "You already have a registration for this event.",
                        "আপনি ইতিমধ্যে এই ইভেন্টে নিবন্ধিত আছেন।");
            }
        }

        return problem(HttpStatus.CONFLICT, "conflict",
                "That conflicts with something already recorded.",
                "এটি আগে থেকে থাকা তথ্যের সাথে সাংঘর্ষিক।");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail onAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden",
                "You are not allowed to do that.", "আপনার এই কাজের অনুমতি নেই।");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception ex) {
        // The member gets a sentence they can act on; the stack trace stays here.
        log.error("Unhandled failure", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "Something went wrong on our side. Please try again.",
                "আমাদের দিকে কিছু সমস্যা হয়েছে। আবার চেষ্টা করুন।");
    }

    private ProblemDetail problem(HttpStatus status, String code, String message, String messageBn) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("code", code);
        problem.setProperty("messageBn", messageBn);
        return problem;
    }
}
