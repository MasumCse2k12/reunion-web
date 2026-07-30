package bd.sammalani.alumni.admin;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.AuditEntryDto;
import bd.sammalani.alumni.admin.AdminDtos.AuditPage;
import bd.sammalani.alumni.admin.AdminDtos.TombstoneDto;
import bd.sammalani.alumni.common.audit.AuditAction;
import bd.sammalani.alumni.common.audit.AuditLog;
import bd.sammalani.alumni.common.audit.AuditLogRepository;
import bd.sammalani.alumni.common.audit.AuditTrail;
import bd.sammalani.alumni.common.audit.TombstoneKind;
import bd.sammalani.alumni.common.audit.Tombstones;
import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.common.web.Cursors;
import bd.sammalani.alumni.config.CacheConfig;
import lombok.RequiredArgsConstructor;

/**
 * Reading the audit trail, and undoing a deletion.
 * <p>
 * Two rules govern who sees what, and they are the same two the review queue
 * follows. A group admin's view of the trail is restricted to their own batch
 * years, because the log records phone numbers and addresses changing and an
 * unrestricted trail would be the "download all members" that §7 of the design
 * doc forbids by another route. And the recycle bin is super admin only:
 * restoring a person can collide with a live record, and deciding that is not a
 * batch coordinator's call.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    /** Postgres rejects an empty IN list; this stands in when a scope is empty. */
    private static final Collection<Integer> NO_BATCHES = List.of(Integer.MIN_VALUE);

    private final AuditLogRepository entries;
    private final Tombstones tombstones;
    private final AdminContextService context;
    private final AuditTrail trail;

    @Transactional(readOnly = true)
    public AuditPage page(String entity, String entityId, UUID actorId, AuditAction action,
                          Integer batchYear, Instant since, Instant until, String cursor, Integer limit) {
        AdminSession admin = context.current();
        // A filter may narrow the caller's authority; it can never widen it.
        if (batchYear != null) {
            context.requireCovers(admin, batchYear);
        }

        int pageSize = Cursors.clampLimit(limit, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        Cursors.SeqPosition position = Cursors.decodeSeq(cursor);
        boolean allBatches = admin.isSuperAdmin();
        Collection<Integer> batches = allBatches || admin.batches().isEmpty() ? NO_BATCHES : admin.batches();

        // One more row than asked for: its existence is what says there is a next
        // page, without a count query per scroll.
        List<AuditLog> rows = entries.findPage(entity, entityId, actorId, action, batchYear, since, until,
                allBatches, batches,
                position == null ? null : position.at(),
                position == null ? null : position.id(),
                Limit.of(pageSize + 1));

        boolean hasMore = rows.size() > pageSize;
        List<AuditLog> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
                ? Cursors.encodeSeq(page.getLast().getAt(), page.getLast().getId())
                : null;

        return new AuditPage(page.stream().map(AdminAuditService::toDto).toList(), nextCursor);
    }

    /* ---------------- the recycle bin ---------------- */

    @Transactional(readOnly = true)
    public List<TombstoneDto> deleted(String kind, Integer limit) {
        requireSuperAdmin();
        TombstoneKind resolved = TombstoneKind.of(kind);
        return tombstones.list(resolved, Cursors.clampLimit(limit, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE)).stream()
                .map(t -> new TombstoneDto(t.id(), resolved.label(), t.label(), t.batchYear(),
                        t.deletedAt(), t.deletedBy(), t.note()))
                .toList();
    }

    /**
     * Bring a soft-deleted row back.
     * <p>
     * The restore is itself audited, with the reason the caller gave. Undoing a
     * removal is as consequential as making one — bringing back a person is
     * bringing back their phone number and their address — so the trail has to
     * record it as its own act rather than leave the row looking as though it was
     * never deleted.
     */
    @Transactional
    @CacheEvict(value = {CacheConfig.ADMIN_STATS, CacheConfig.BATCH_COVERAGE, CacheConfig.BATCH_TOTALS,
            CacheConfig.NOTICES}, allEntries = true)
    public void restore(String kind, UUID id, String reason) {
        AdminSession admin = requireSuperAdmin();
        TombstoneKind resolved = TombstoneKind.of(kind);

        if (!tombstones.restore(resolved, id)) {
            throw ApiException.notFound("Nothing deleted under that id.",
                    "এই আইডিতে মুছে ফেলা কিছু নেই।");
        }

        trail.note(reason == null || reason.isBlank()
                ? "Restored by " + admin.username()
                : reason.strip());
        trail.record(AuditAction.RESTORE, resolved.table(), id.toString(), null);
    }

    private AdminSession requireSuperAdmin() {
        AdminSession session = context.current();
        if (!session.isSuperAdmin()) {
            throw ApiException.forbidden(
                    "Only a super admin can do that.", "শুধু সুপার অ্যাডমিন এই কাজটি করতে পারেন।");
        }
        return session;
    }

    private static AuditEntryDto toDto(AuditLog row) {
        return new AuditEntryDto(
                row.getId(), row.getAt(), row.getAction(), row.getEntity(), row.getEntityId(),
                row.getBatchYear(), row.getActorId(), row.getActorKind(), row.getActorLabel(),
                row.getNote(), row.getChanges() == null ? Map.of() : row.getChanges(),
                row.getRequestId(), row.getIp(), row.getMethod(), row.getPath());
    }
}
