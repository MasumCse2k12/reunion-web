package bd.sammalani.alumni.admin;

import java.util.Set;

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
                Set.copyOf(credential.getBatches()),
                credential.isActive(),
                credential.isMustChange(),
                credential.getCreatedAt());
    }
}
