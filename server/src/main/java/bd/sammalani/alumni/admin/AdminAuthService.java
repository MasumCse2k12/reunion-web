package bd.sammalani.alumni.admin;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.AdminAccountDto;
import bd.sammalani.alumni.admin.AdminDtos.AdminLoginResponse;
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

    @Transactional
    public AdminLoginResponse login(String username, String password) {
        AdminCredential credential = admins.findByUsernameIgnoringCase(username.strip())
                .orElseThrow(AdminAuthService::wrongCredentials);

        if (credential.isLocked()) {
            throw ApiException.tooManyRequests(
                    "Too many failed attempts. Try again shortly.",
                    "অনেকবার ভুল হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।");
        }
        if (!credential.isActive()) {
            throw wrongCredentials();
        }
        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            registerFailure(credential);
            throw wrongCredentials();
        }

        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credential.setLastLoginAt(Instant.now());
        admins.save(credential);

        log.info("Admin {} signed in", credential.getUsername());
        return new AdminLoginResponse(
                jwt.issueAdminAccess(credential.getPersonId(), credential.getPerson().getName()),
                props.jwt().adminAccessTtl().toSeconds(),
                AdminAccountMapper.toDto(credential));
    }

    /**
     * Lock the account for a while once the attempts run out. A lockout rather
     * than a permanent disable, because the usual cause is a coordinator who has
     * forgotten which of two passwords they set.
     */
    private void registerFailure(AdminCredential credential) {
        int attempts = credential.getFailedAttempts() + 1;
        credential.setFailedAttempts(attempts);
        if (attempts >= props.security().maxFailedLogins()) {
            credential.setLockedUntil(Instant.now().plus(props.security().lockoutDuration()));
            credential.setFailedAttempts(0);
            log.warn("Admin {} locked out after repeated failures", credential.getUsername());
        }
        admins.save(credential);
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
