package bd.sammalani.alumni.admin;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.AdminAccountDto;
import bd.sammalani.alumni.admin.AdminDtos.AdminLoginResponse;
import bd.sammalani.alumni.common.audit.ActorKind;
import bd.sammalani.alumni.common.audit.AuditAction;
import bd.sammalani.alumni.common.audit.AuditContext;
import bd.sammalani.alumni.common.audit.AuditTrail;
import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.config.AppProperties;
import bd.sammalani.alumni.domain.admin.AdminCredential;
import bd.sammalani.alumni.domain.admin.AdminRepository;
import bd.sammalani.alumni.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Password sign-in, for admins only.
 * <p>
 * The failure path is deliberately uniform: a username that does not exist, a
 * wrong password, and a disabled account all produce the same message. Telling
 * an attacker which of the three they hit is telling them which usernames are
 * real.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    private final AdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final AppProperties props;
    private final AdminContextService context;
    private final AdminLoginAttempts attempts;
    private final AuditTrail trail;

    @Transactional
    public AdminLoginResponse login(String rawUsername, String password) {
        String username = rawUsername.strip();
        AdminCredential credential = admins.findByUsernameIgnoringCase(username).orElse(null);

        // Every refusal below is recorded through AdminLoginAttempts, which commits
        // on its own. This method's transaction is about to be rolled back by the
        // exception, and a trail that only survives the successful sign-ins is a
        // trail with the interesting half missing.
        if (credential == null) {
            attempts.refused(username, "no such account");
            throw wrongCredentials();
        }
        if (credential.isLocked()) {
            attempts.refused(username, "locked out");
            throw ApiException.tooManyRequests(
                    "Too many failed attempts. Try again shortly.",
                    "অনেকবার ভুল হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।");
        }
        if (!credential.isActive()) {
            attempts.refused(username, "account disabled");
            throw wrongCredentials();
        }
        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            attempts.wrongPassword(credential.getPersonId(), username);
            throw wrongCredentials();
        }

        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credential.setLastLoginAt(Instant.now());

        recordSignIn(credential);
        log.info("Admin {} signed in", credential.getUsername());
        return new AdminLoginResponse(
                jwt.issueAdminAccess(credential.getPersonId(), credential.getPerson().getName()),
                props.jwt().adminAccessTtl().toSeconds(),
                AdminAccountMapper.toDto(credential));
    }

    /**
     * Save the sign-in and record it, both as the person who just proved who they
     * are rather than as the anonymous request that arrived. This is the only place
     * in the application where the actor changes mid-request, and it is why
     * {@code AuditActor} can be re-identified at all.
     * <p>
     * <strong>{@code saveAndFlush}, and the flush is the point.</strong> Hibernate
     * would otherwise defer the update to the commit-time flush, which happens as
     * the transaction proxy unwinds — after this scope has closed — and the audit
     * row for {@code lastLoginAt} would come out attributed to "anonymous" while
     * the LOGIN row beside it named the coordinator. Two rows, one request, two
     * different actors, and the more suspicious-looking of the two would be the
     * wrong one. Flushing here puts the entity event inside the binding.
     */
    private void recordSignIn(AdminCredential credential) {
        var actor = AuditContext.current()
                .identified(credential.getPersonId(), ActorKind.ADMIN, credential.getUsername());
        AuditContext.runAs(actor, () -> {
            admins.saveAndFlush(credential);
            trail.record(AuditAction.LOGIN, AdminLoginAttempts.SUBJECT, credential.getUsername(), null);
        });
    }

    @Transactional(readOnly = true)
    public AdminAccountDto me() {
        AdminSession session = context.current();
        return new AdminAccountDto(session.personId(), session.name(), session.nameBn(), session.username(),
                null, session.role(), session.batches(), session.active(), session.mustChangePassword(), null);
    }

    private static ApiException wrongCredentials() {
        return ApiException.unauthorized("Wrong username or password.", "ব্যবহারকারীর নাম বা পাসওয়ার্ড ভুল।");
    }
}
