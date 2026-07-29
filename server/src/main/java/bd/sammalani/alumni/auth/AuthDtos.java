package bd.sammalani.alumni.auth;

import java.util.UUID;

import bd.sammalani.alumni.domain.person.PersonDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request and response shapes for the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record OtpRequest(
            @NotBlank @Schema(example = "01712345678") String phone) {
    }

    public record ClaimRequest(
            @NotNull UUID personId,
            @NotBlank String phone) {
    }

    public record SelfRegisterRequest(
            @NotBlank String name,
            String nameBn,
            @NotNull Integer batchYear,
            @NotBlank String phone) {
    }

    @Schema(description = "Quote challengeId back with the code. devCode is only ever populated on a dev deployment.")
    public record ChallengeResponse(String challengeId, long expiresInSeconds, String devCode) {
    }

    public record OtpVerifyRequest(
            @NotBlank String challengeId,
            @NotBlank String code) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record SessionResponse(String accessToken, String refreshToken, long expiresInSeconds, PersonDto person) {
    }
}
