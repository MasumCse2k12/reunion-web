package bd.sammalani.alumni.admin;

import bd.sammalani.alumni.admin.AdminDtos.AdminAccountDto;
import bd.sammalani.alumni.domain.admin.AdminCredential;

/**
 * Credential to DTO. Exists mainly to make it structurally impossible to return
 * a password hash: there is one mapping, and it does not mention the field.
 */
final class AdminAccountMapper {

    private AdminAccountMapper() {
    }

    static AdminAccountDto toDto(AdminCredential credential) {
        return new AdminAccountDto(
                credential.getPersonId(),
                credential.getPerson().getName(),
                credential.getPerson().getNameBn(),
                credential.getUsername(),
                credential.getPerson().getPhone(),
                credential.getRole(),
                // Ascending — the portal reads the ends of this as the ends of
                // the assignment. See AdminSession#sorted.
                AdminSession.sorted(credential.getBatches()),
                credential.isActive(),
                credential.isMustChange(),
                credential.getCreatedAt());
    }
}
