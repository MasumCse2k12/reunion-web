package bd.sammalani.alumni.admin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
                sorted(credential.getBatches()),
                credential.isActive(),
                credential.isMustChange());
    }

    /**
     * Ascending, and in a set that keeps that order.
     * <p>
     * {@code Set.copyOf} would be the obvious call here and it is the wrong one:
     * its iteration order is deliberately randomised per JVM, so the years reach
     * the portal shuffled. The portal reads the first and last element as the
     * ends of the assignment — "SSC 1998–2015" on the account card, and the
     * from/to boxes on the edit form — which is nonsense on a shuffled list and,
     * worse, gets written back on the next save. The order is part of the
     * contract, so it is established here rather than hoped for downstream.
     */
    static Set<Integer> sorted(Collection<Integer> batches) {
        return batches.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
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
