package bd.sammalani.alumni.admin;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.admin.AdminDtos.ClaimDecisionRequest;
import bd.sammalani.alumni.admin.AdminDtos.ClaimDto;
import bd.sammalani.alumni.common.web.CursorPage;
import bd.sammalani.alumni.domain.person.PersonStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The identity queue. Like the application queue, every route here resolves the
 * caller's batch scope server-side; a filter can narrow it and nothing can
 * widen it.
 */
@RestController
@RequestMapping("/api/v1/admin/claims")
@Tag(name = "Admin · Identity queue", description = "Verifying that a claimed name really belongs to that batch")
@RequiredArgsConstructor
public class AdminClaimController {

    private final AdminClaimService claims;

    @GetMapping
    @Operation(summary = "People who have claimed a name, newest first",
            description = """
                    Defaults to CLAIMED — those still waiting on a coordinator. \
                    VERIFIED and REJECTED are readable for looking a past decision up; \
                    SEEDED is not a claim and is refused. \
                    Keyset pagination: hand back `nextCursor` unread for the next page.""")
    public CursorPage<ClaimDto> list(
            @RequestParam(required = false, name = "status") PersonStatus status,
            @RequestParam(required = false, name = "batchYear") Integer batchYear,
            @RequestParam(required = false, name = "q") String search,
            @RequestParam(required = false, name = "cursor") String cursor,
            @RequestParam(required = false, name = "limit") Integer limit) {
        return claims.page(status, batchYear, search, cursor, limit);
    }

    @PostMapping("/{personId}/verify")
    @Operation(summary = "Verify or refuse one claimed identity",
            description = """
                    This decides who someone is, not whether they are coming: it does not \
                    approve or refuse any registration they may have sent in. A rejection \
                    requires a reason. A claim already decided cannot be decided the same \
                    way twice, and only a super admin can decide it the other way.""")
    public ClaimDto verify(@PathVariable("personId") UUID personId,
                           @Valid @RequestBody ClaimDecisionRequest request) {
        return claims.decide(personId, request.decision(), request.note());
    }
}
