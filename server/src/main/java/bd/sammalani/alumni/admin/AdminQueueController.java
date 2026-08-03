package bd.sammalani.alumni.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.admin.AdminDtos.ApplicationDto;
import bd.sammalani.alumni.admin.AdminDtos.BulkDecisionResponse;
import bd.sammalani.alumni.admin.AdminDtos.BulkMemberDecisionRequest;
import bd.sammalani.alumni.admin.AdminDtos.BulkPaymentDecisionRequest;
import bd.sammalani.alumni.admin.AdminDtos.MemberDecisionRequest;
import bd.sammalani.alumni.admin.AdminDtos.MemberStatus;
import bd.sammalani.alumni.admin.AdminDtos.PaymentDecisionRequest;
import bd.sammalani.alumni.admin.AdminDtos.QueueKind;
import bd.sammalani.alumni.common.web.CursorPage;
import bd.sammalani.alumni.domain.payment.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The coordinator's queue. Every route here resolves the caller's batch scope
 * server-side; a filter can narrow it and nothing can widen it.
 */
@RestController
@RequestMapping("/api/v1/admin/applications")
@Tag(name = "Admin · Review queue", description = "Member verification and payment confirmation")
@RequiredArgsConstructor
public class AdminQueueController {

    private final AdminQueueService queue;

    @GetMapping
    @Operation(summary = "One page of the queue, newest first",
            description = """
                    Keyset pagination: hand back `nextCursor` unread to get the next page. \
                    The cursor is opaque and a stale one simply starts from the top. \
                    `total` counts everything matching the filter, not the page.

                    `queue=PAYMENTS` reads the money queue, which is approved members only \
                    — a `memberStatus` sent alongside it is ignored rather than honoured, \
                    because a payment belongs to a seat somebody granted.""")
    public CursorPage<ApplicationDto> list(
            @RequestParam(required = false, name = "queue") QueueKind kind,
            @RequestParam(required = false, name = "memberStatus") MemberStatus memberStatus,
            @RequestParam(required = false, name = "paymentStatus") PaymentStatus paymentStatus,
            @RequestParam(required = false, name = "batchYear") Integer batchYear,
            @RequestParam(required = false, name = "q") String search,
            @RequestParam(required = false, name = "cursor") String cursor,
            @RequestParam(required = false, name = "limit") Integer limit) {
        return queue.page(kind, memberStatus, paymentStatus, batchYear, search, cursor, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One application in full")
    public ApplicationDto one(@PathVariable("id") UUID id) {
        return queue.one(id);
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Approve or reject one member")
    public ApplicationDto verify(@PathVariable("id") UUID id, @Valid @RequestBody MemberDecisionRequest request) {
        return queue.decideMember(id, request.decision(), request.note());
    }

    @PostMapping("/{id}/payment")
    @Operation(summary = "Confirm or reject one payment")
    public ApplicationDto payment(@PathVariable("id") UUID id, @Valid @RequestBody PaymentDecisionRequest request) {
        return queue.decidePayment(id, request.decision(), request.note());
    }

    @PostMapping("/verify")
    @Operation(summary = "Approve or reject many members at once",
            description = """
                    Partial by design: each id is decided on its own and anything refused \
                    comes back in `skipped` with the reason, rather than failing the batch. \
                    A rejection still requires a note, which is written onto every row.""")
    public BulkDecisionResponse verifyMany(@Valid @RequestBody BulkMemberDecisionRequest request) {
        return queue.decideMembers(request.ids(), request.decision(), request.note());
    }

    @PostMapping("/payment")
    @Operation(summary = "Confirm or reject many payments at once",
            description = "A payment cannot be confirmed for someone who has not reported one; "
                    + "those rows come back in `skipped`.")
    public BulkDecisionResponse paymentMany(@Valid @RequestBody BulkPaymentDecisionRequest request) {
        return queue.decidePayments(request.ids(), request.decision(), request.note());
    }

    @GetMapping("/batch-years")
    @Operation(summary = "Batch years this admin may act on, for the filter dropdown")
    public List<Integer> batchYears() {
        return queue.batchYearsForCurrentAdmin();
    }
}
