package bd.sammalani.alumni.admin;

import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.config.CacheConfig;
import bd.sammalani.alumni.domain.admin.AdminRepository;
import bd.sammalani.alumni.security.CurrentUser;
import lombok.RequiredArgsConstructor;

/**
 * Resolves the caller's authority for every admin request.
 * <p>
 * This is the one place batch scope is decided, and it is decided from the
 * database rather than from the token: a coordinator removed from the 1974 batch
 * at 10:00 must not still be approving 1974 registrations at 10:01 because their
 * eight-hour token says so. The cache is short and is evicted by name whenever an
 * account is edited, so the common case costs no query and the uncommon case is
 * still correct.
 */
@Service
@RequiredArgsConstructor
public class AdminContextService {

    private final AdminRepository admins;

    /** The signed-in admin, or 401 if the account has since been disabled. */
    public AdminSession current() {
        AdminSession session = sessionOf(CurrentUser.admin().personId());
        if (!session.active()) {
            throw ApiException.unauthorized(
                    "This account has been disabled.", "এই অ্যাকাউন্টটি বন্ধ করা হয়েছে।");
        }
        return session;
    }

    @Cacheable(value = CacheConfig.ADMIN_SCOPE, key = "#personId")
    @Transactional(readOnly = true)
    public AdminSession sessionOf(UUID personId) {
        return admins.findWithPersonByPersonId(personId)
                .map(AdminSession::from)
                .orElseThrow(() -> ApiException.unauthorized(
                        "Your admin session has ended. Please sign in again.",
                        "আপনার অ্যাডমিন সেশন শেষ হয়েছে। আবার লগইন করুন।"));
    }

    /** Called whenever a role, a batch assignment or the active flag changes. */
    @CacheEvict(value = CacheConfig.ADMIN_SCOPE, key = "#personId")
    public void invalidate(UUID personId) {
        // The annotation is the whole method.
    }

    public void requireCovers(AdminSession session, Integer batchYear) {
        if (!session.covers(batchYear)) {
            throw ApiException.outsideBatchScope();
        }
    }
}
