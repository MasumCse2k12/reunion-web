package bd.sammalani.alumni.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.admin.AdminDtos.AdminAccountDto;
import bd.sammalani.alumni.admin.AdminDtos.AdminLoginResponse;
import bd.sammalani.alumni.admin.AdminDtos.AdminStatsDto;
import bd.sammalani.alumni.admin.AdminDtos.CreateAdminRequest;
import bd.sammalani.alumni.admin.AdminDtos.LoginRequest;
import bd.sammalani.alumni.admin.AdminDtos.SetPasswordRequest;
import bd.sammalani.alumni.admin.AdminDtos.UpdateAdminRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin · Portal", description = "Sign-in, overview and coordinator accounts")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService auth;
    private final AdminStatsService stats;
    private final AdminAccountService accounts;

    @PostMapping("/auth/login")
    @SecurityRequirements
    @Operation(summary = "Sign in with a username and password",
            description = "Returns a token of the ADMIN audience. A member token is never accepted here, and this one is never accepted on a member route.")
    public AdminLoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End the admin session")
    public void logout() {
        // Stateless tokens; the client discards it. Kept for symmetry and for
        // the day server-side revocation is added.
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in admin, with their batch scope")
    public AdminAccountDto me() {
        return auth.me();
    }

    @GetMapping("/stats")
    @Operation(summary = "Overview tiles, scoped to the caller's batches")
    public AdminStatsDto stats() {
        return stats.statsFor(stats.currentSession());
    }

    /* ---------------- accounts: super admin only ---------------- */

    @GetMapping("/accounts")
    @Operation(summary = "All admin accounts")
    public List<AdminAccountDto> accounts() {
        return accounts.list();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a coordinator and assign their batches")
    public AdminAccountDto create(@Valid @RequestBody CreateAdminRequest request) {
        return accounts.create(request);
    }

    @PatchMapping("/accounts/{id}")
    @Operation(summary = "Edit a coordinator's details, batches or active flag")
    public AdminAccountDto update(@PathVariable UUID id, @Valid @RequestBody UpdateAdminRequest request) {
        return accounts.update(id, request);
    }

    @PostMapping("/accounts/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a coordinator's password", description = "Returns nothing and never echoes the value.")
    public void setPassword(@PathVariable UUID id, @Valid @RequestBody SetPasswordRequest request) {
        accounts.setPassword(id, request.password());
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove admin access",
            description = "Deletes the credential, not the person — they remain an alum.")
    public void revoke(@PathVariable UUID id) {
        accounts.revoke(id);
    }
}
