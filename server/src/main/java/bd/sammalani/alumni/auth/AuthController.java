package bd.sammalani.alumni.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.auth.AuthDtos.ChallengeResponse;
import bd.sammalani.alumni.auth.AuthDtos.OtpRequest;
import bd.sammalani.alumni.auth.AuthDtos.OtpVerifyRequest;
import bd.sammalani.alumni.auth.AuthDtos.RefreshRequest;
import bd.sammalani.alumni.auth.AuthDtos.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Member sign-in by one-time code")
@SecurityRequirements
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    @PostMapping("/otp/request")
    @Operation(summary = "Send a one-time code to a number we already know")
    public ChallengeResponse requestCode(@Valid @RequestBody OtpRequest request) {
        return auth.requestLoginCode(request.phone());
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Exchange a code for a session")
    public SessionResponse verify(@Valid @RequestBody OtpVerifyRequest request) {
        return auth.verify(request.challengeId(), request.code());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public SessionResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End the session",
            description = "Tokens are stateless and short-lived, so this is the client discarding them. "
                    + "Kept as an endpoint so that adding server-side revocation later needs no client change.")
    public void logout() {
        // Intentionally empty — see the description above.
    }
}
