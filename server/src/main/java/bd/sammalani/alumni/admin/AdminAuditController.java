package bd.sammalani.alumni.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.admin.AdminDtos.AuditPage;
import bd.sammalani.alumni.admin.AdminDtos.RestoreRequest;
import bd.sammalani.alumni.admin.AdminDtos.TombstoneDto;
import bd.sammalani.alumni.common.audit.AuditAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The audit trail, and the bin of everything that has been soft-deleted.
 * <p>
 * Read-only apart from one route, and that route only un-deletes. There is
 * deliberately nothing here that can edit or remove an audit row: a trail its own
 * subjects can rewrite is a trail nobody should believe, and the absence of the
 * endpoint is the enforcement.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin · Audit", description = "Who changed what, and undoing a deletion")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditService audit;

    @GetMapping("/audit")
    @Operation(summary = "The audit trail, newest first",
            description = """
                    Every insert, update and soft delete, plus the sign-ins and authority \
                    changes no row change would show. Keyset-paginated: hand back `nextCursor` \
                    unread. A group admin sees their own batch years only.

                    There is no `total` — this is the one table that grows without bound and \
                    counting it would be a full scan for a number nobody reads.""")
    public AuditPage list(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Integer batchYear,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return audit.page(entity, entityId, actorId, action, batchYear, since, until, cursor, limit);
    }

    @GetMapping("/deleted/{kind}")
    @Operation(summary = "What has been soft-deleted, and by whom",
            description = """
                    `kind` is one of person, registration, payment, notice or referral. \
                    Super admin only. Anything else is a 404 — which table names exist is \
                    not a caller's business.""")
    public List<TombstoneDto> deleted(@PathVariable String kind,
                                      @RequestParam(required = false) Integer limit) {
        return audit.deleted(kind, limit);
    }

    @PostMapping("/deleted/{kind}/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bring a soft-deleted row back",
            description = """
                    Clears the tombstone, and records the restore in the trail with the reason \
                    given. May return 409: a person's mobile number can have been claimed by \
                    somebody else while they were removed, and no automatic rule for that \
                    would be the right one.""")
    public void restore(@PathVariable String kind, @PathVariable UUID id,
                        @Valid @RequestBody(required = false) RestoreRequest request) {
        audit.restore(kind, id, request == null ? null : request.reason());
    }
}
