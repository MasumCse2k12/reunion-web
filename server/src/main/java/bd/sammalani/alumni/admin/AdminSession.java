package bd.sammalani.alumni.admin;

import java.util.Set;
import java.util.UUID;

import bd.sammalani.alumni.domain.admin.AdminCredential;
import bd.sammalani.alumni.domain.admin.AdminRole;

/**
 * An admin's identity and authority, as re-read from the database on each
 * request (through a short-lived cache) rather than trusted from the token.
 * <p>
 * {@link #scopeOrNull()} returns null for a super admin, and null is what the
 * query layer reads as "no batch restriction". A group admin with no batches
 * assigned gets an empty set, which means nothing — never everything. The
 * difference between those two cases is the whole of this class.
 */
public record AdminSession(
        UUID personId,
        String name,
        String nameBn,
        String username,
        AdminRole role,
        Set<Integer> batches,
        boolean active,
        boolean mustChangePassword) {

    public static AdminSession from(AdminCredential credential) {
        return new AdminSession(
                credential.getPersonId(),
                credential.getPerson().getName(),
                credential.getPerson().getNameBn(),
                credential.getUsername(),
                credential.getRole(),
                Set.copyOf(credential.getBatches()),
                credential.isActive(),
                credential.isMustChange());
    }

    public boolean isSuperAdmin() {
        return role == AdminRole.SUPER_ADMIN;
    }

    public boolean covers(Integer batchYear) {
        return isSuperAdmin() || (batchYear != null && batches.contains(batchYear));
    }

    /** Null means all 59 batches; any set means exactly those years. */
    public Set<Integer> scopeOrNull() {
        return isSuperAdmin() ? null : batches;
    }
}
