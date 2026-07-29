package bd.sammalani.alumni.domain.batch;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.config.CacheConfig;
import lombok.RequiredArgsConstructor;

/**
 * Batch coverage — "1,842 of an estimated 4,900 alumni found" — which is the
 * first thing anyone sees and one of the most expensive things to compute.
 * Hence the cache, and hence the explicit eviction when a claim changes it.
 */
@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository batches;

    @Cacheable(CacheConfig.BATCH_COVERAGE)
    @Transactional(readOnly = true)
    public List<BatchDto> coverage() {
        return batches.coverage().stream()
                .map(row -> new BatchDto(row.getYear(), row.getRosterCount(), row.getClaimedCount()))
                .toList();
    }

    @Cacheable(CacheConfig.BATCH_TOTALS)
    @Transactional(readOnly = true)
    public Totals totals() {
        List<BatchDto> all = coverage();
        return new Totals(
                all.stream().mapToLong(BatchDto::rosterCount).sum(),
                all.stream().mapToLong(BatchDto::claimedCount).sum(),
                all.size());
    }

    /** Called whenever a person is claimed or verified, which moves the counters. */
    @CacheEvict(value = {CacheConfig.BATCH_COVERAGE, CacheConfig.BATCH_TOTALS}, allEntries = true)
    public void invalidateCoverage() {
        // The annotation is the whole method.
    }

    public record BatchDto(int year, long rosterCount, long claimedCount) {
    }

    public record Totals(long roster, long claimed, int batches) {
    }
}
