package bd.sammalani.alumni.admin;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.common.audit.AuditAction;
import bd.sammalani.alumni.common.audit.AuditTrail;
import bd.sammalani.alumni.config.AppProperties;
import bd.sammalani.alumni.domain.admin.AdminCredential;
import bd.sammalani.alumni.domain.admin.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Everything a refused sign-in has to leave behind, written in its own
 * transaction.
 * <p>
 * <strong>Why this is a separate bean.</strong> A refused sign-in ends in a
 * thrown {@code ApiException}, which rolls the caller's transaction back — and
 * that takes the failed-attempt counter with it. The lockout in
 * {@link AdminAuthService} was therefore counting to five in memory and forgetting
 * every time, so no account could ever actually lock. The same rollback would
 * discard the audit row, which is worse: the log would show successful sign-ins
 * and nothing else, and a password-guessing run against a coordinator's account
 * would be invisible in exactly the record that exists to reveal it.
 * <p>
 * {@code REQUIRES_NEW} is what fixes both, and it has to be a different bean
 * because a self-invocation would not pass through the proxy that applies it.
 * The credential is re-read here rather than passed in, because the caller's
 * instance belongs to a persistence context that is about to be discarded.
 * <p>
 * Attempts are keyed by the username the caller typed, not by a person id. That
 * is the question an investigation asks — "how many times did someone try to be
 * <em>superadmin</em>" — and it is the only key available when the username does
 * not exist at all.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class AdminLoginAttempts {

    static final String SUBJECT = "admin_credential";

    private final AdminRepository admins;
    private final AppProperties props;
    private final AuditTrail trail;

    /**
     * A wrong password against a real account: count it, lock the account if the
     * attempts have run out, and record both.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void wrongPassword(UUID personId, String username) {
        AdminCredential credential = admins.findById(personId).orElse(null);
        if (credential == null) {
            refused(username, "wrong password");
            return;
        }

        int attempts = credential.getFailedAttempts() + 1;
        credential.setFailedAttempts(attempts);

        boolean lockedOut = attempts >= props.security().maxFailedLogins();
        if (lockedOut) {
            credential.setLockedUntil(Instant.now().plus(props.security().lockoutDuration()));
            // Reset rather than leave it at the limit, so the next lockout needs a
            // fresh run of failures instead of a single one.
            credential.setFailedAttempts(0);
        }
        admins.save(credential);

        refused(username, lockedOut ? "wrong password, attempts exhausted" : "wrong password");
        if (lockedOut) {
            trail.record(AuditAction.LOCKOUT, SUBJECT, username, null);
            log.warn("Admin {} locked out after repeated failures", username);
        }
    }

    /**
     * A username that does not exist, or an account that has been disabled. Both
     * produce the same response to the caller — telling them which is telling them
     * which usernames are real — but the trail records the difference, because the
     * two mean very different things to whoever reads it later.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void refused(String username, String why) {
        trail.note(why);
        trail.record(AuditAction.LOGIN_FAILED, SUBJECT, username, null);
    }
}
