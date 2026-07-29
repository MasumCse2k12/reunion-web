package bd.sammalani.alumni.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import bd.sammalani.alumni.domain.admin.AdminRole;
import bd.sammalani.alumni.domain.payment.PaymentStatus;
import bd.sammalani.alumni.domain.person.Gender;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.GuestDto;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.PaymentDto;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.ReviewDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AdminDtos {

    private AdminDtos() {
    }

    /* ---------------- session ---------------- */

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record AdminLoginResponse(String accessToken, long expiresInSeconds, AdminAccountDto admin) {
    }

    public record AdminAccountDto(UUID id, String name, String nameBn, String username, String phone,
                                  AdminRole role, Set<Integer> batches, boolean active,
                                  boolean mustChangePassword, Instant createdAt) {
    }

    /* ---------------- the review queue ---------------- */

    /**
     * What a coordinator judges. Note this is a read model assembled from a
     * registration, its person, its latest payment claim and the latest decision
     * on each — never a table.
     */
    public record ApplicationDto(
            UUID id,
            UUID personId,
            String name,
            String nameBn,
            Integer batchYear,
            String phone,
            String email,
            Gender gender,
            LocalDate dob,
            String bloodGroup,
            String occupation,
            String city,
            List<GuestDto> guests,
            BigDecimal amountDue,
            Instant submittedAt,
            String memberNote,
            MemberStatus memberStatus,
            ReviewDto memberReview,
            PaymentStatus paymentStatus,
            PaymentDto payment,
            ReviewDto paymentReview) {
    }

    /**
     * The queue's view of a submission. A registration that has been sent in but
     * not yet judged is PENDING here, whatever the storage calls it.
     */
    public enum MemberStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    public enum MemberVerdict {
        APPROVED,
        REJECTED
    }

    public enum PaymentVerdict {
        CONFIRMED,
        REJECTED
    }

    public record MemberDecisionRequest(
            @NotNull MemberVerdict decision,
            @Size(max = 500) String note) {
    }

    public record PaymentDecisionRequest(
            @NotNull PaymentVerdict decision,
            @Size(max = 500) String note) {
    }

    @Schema(description = "One decision applied to many applications. The note is written onto every one of them.")
    public record BulkMemberDecisionRequest(
            @NotEmpty List<UUID> ids,
            @NotNull MemberVerdict decision,
            @Size(max = 500) String note) {
    }

    public record BulkPaymentDecisionRequest(
            @NotEmpty List<UUID> ids,
            @NotNull PaymentVerdict decision,
            @Size(max = 500) String note) {
    }

    /**
     * Deliberately partial rather than all-or-nothing: a coordinator who ticks
     * forty rows should not lose thirty-nine sound decisions because one row
     * moved out of their scope while they were reading. Anything refused comes
     * back named, so the portal can say why.
     */
    public record BulkDecisionResponse(List<ApplicationDto> updated, List<SkippedDto> skipped) {
    }

    public record SkippedDto(UUID id, String name, String reason, String reasonBn) {
    }

    /* ---------------- overview ---------------- */

    public record AdminStatsDto(
            long pendingMembers,
            long pendingPayments,
            long approvedMembers,
            long rejectedMembers,
            BigDecimal confirmedAmount,
            BigDecimal outstandingAmount,
            int batchesCovered) {
    }

    /* ---------------- account management ---------------- */

    public record CreateAdminRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String nameBn,
            @NotBlank @Size(min = 3, max = 40) String username,
            @NotBlank @Size(min = 10, max = 100) String password,
            @NotBlank String phone,
            @NotNull AdminRole role,
            Set<Integer> batches) {
    }

    public record UpdateAdminRequest(
            @Size(max = 120) String name,
            @Size(max = 120) String nameBn,
            String phone,
            Set<Integer> batches,
            Boolean active) {
    }

    public record SetPasswordRequest(@NotBlank @Size(min = 10, max = 100) String password) {
    }
}
