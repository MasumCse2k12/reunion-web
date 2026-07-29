package bd.sammalani.alumni.domain.registration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import bd.sammalani.alumni.domain.payment.PaymentMethod;
import bd.sammalani.alumni.domain.payment.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    public record GuestInput(
            UUID id,
            @NotBlank @Size(max = 120) String name,
            @NotNull GuestRelation relation,
            @Min(0) Integer age,
            String tshirtSize) {
    }

    /** A draft save. Sending it again replaces the guest list wholesale. */
    public record RegistrationInput(
            @Valid List<GuestInput> guests,
            String tshirtSize,
            FoodPreference foodPref,
            @Size(max = 500) String memberNote) {
    }

    public record PaymentReportInput(
            @NotNull PaymentMethod method,
            @NotBlank @Size(max = 80) String reference,
            @NotNull @Positive BigDecimal amount,
            UUID paidToId) {
    }

    public record GuestDto(UUID id, String name, GuestRelation relation, Integer age,
                           String ticketTypeCode, String tshirtSize, BigDecimal amount) {
    }

    public record RegistrationDto(
            UUID id,
            UUID personId,
            Integer batchYear,
            List<GuestDto> guests,
            String tshirtSize,
            FoodPreference foodPref,
            String memberNote,
            BigDecimal amountDue,
            RegistrationStatus status,
            PaymentStatus paymentStatus,
            Instant submittedAt,
            String qrToken,
            ReviewDto memberReview,
            ReviewDto paymentReview,
            PaymentDto payment) {
    }

    public record ReviewDto(String adminName, Instant at, String note) {
    }

    public record PaymentDto(PaymentMethod method, String reference, BigDecimal amount,
                             Instant reportedAt, PaymentStatus status) {
    }

    /** Who to pay: name and number, and nothing else about that person. */
    public record CoordinatorDto(UUID id, String name, String nameBn, String phone) {
    }
}
