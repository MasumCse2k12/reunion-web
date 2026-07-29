package bd.sammalani.alumni.admin;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.AdminStatsDto;
import bd.sammalani.alumni.config.AppProperties;
import bd.sammalani.alumni.config.CacheConfig;
import bd.sammalani.alumni.domain.payment.PaymentStatus;
import bd.sammalani.alumni.domain.registration.RegistrationRepository;
import bd.sammalani.alumni.domain.registration.RegistrationStatus;
import lombok.RequiredArgsConstructor;

/**
 * The overview tiles. Six aggregates that would otherwise be recomputed on every
 * page load, cached for a minute and keyed by the admin so one coordinator's
 * scope never leaks into another's numbers.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    /** Postgres rejects an empty IN list; this stands in when a scope is empty. */
    private static final Collection<Integer> NO_BATCHES = List.of(Integer.MIN_VALUE);

    private final RegistrationRepository registrations;
    private final AdminContextService context;
    private final AppProperties props;

    @Cacheable(value = CacheConfig.ADMIN_STATS, key = "#session.personId()")
    @Transactional(readOnly = true)
    public AdminStatsDto statsFor(AdminSession session) {
        Set<Integer> scope = session.scopeOrNull();
        boolean allBatches = scope == null;
        Collection<Integer> batches = allBatches || scope.isEmpty() ? NO_BATCHES : scope;

        return new AdminStatsDto(
                registrations.countByStatusInScope(RegistrationStatus.SUBMITTED, allBatches, batches),
                registrations.countByPaymentStatusInScope(PaymentStatus.REPORTED, allBatches, batches),
                registrations.countByStatusInScope(RegistrationStatus.APPROVED, allBatches, batches),
                registrations.countByStatusInScope(RegistrationStatus.REJECTED, allBatches, batches),
                orZero(registrations.confirmedAmount(allBatches, batches)),
                orZero(registrations.outstandingAmount(allBatches, batches)),
                allBatches ? props.event().lastBatch() - props.event().firstBatch() + 1 : scope.size());
    }

    /**
     * The caller's session, resolved once. Callers pass it to
     * {@link #statsFor(AdminSession)} rather than having this class call itself —
     * a self-invocation would go straight past the cache proxy.
     */
    public AdminSession currentSession() {
        return context.current();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
