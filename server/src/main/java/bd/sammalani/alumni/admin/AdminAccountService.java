package bd.sammalani.alumni.admin;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.AdminAccountDto;
import bd.sammalani.alumni.admin.AdminDtos.CreateAdminRequest;
import bd.sammalani.alumni.admin.AdminDtos.UpdateAdminRequest;
import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.common.util.PhoneNumbers;
import bd.sammalani.alumni.config.CacheConfig;
import bd.sammalani.alumni.domain.admin.AdminCredential;
import bd.sammalani.alumni.domain.admin.AdminRepository;
import bd.sammalani.alumni.domain.admin.AdminRole;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.person.PersonStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creating and editing coordinators — super admin only, checked here rather
 * than only in the routing rules.
 * <p>
 * Every mutation evicts the actor's cached scope, because the cost of a
 * coordinator briefly keeping authority they were just stripped of is not worth
 * the query it saves.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAccountService {

    private final AdminRepository admins;
    private final PersonRepository people;
    private final PasswordEncoder passwordEncoder;
    private final AdminContextService context;

    @Transactional(readOnly = true)
    public List<AdminAccountDto> list() {
        requireSuperAdmin();
        return admins.findAllWithPerson().stream().map(AdminAccountMapper::toDto).toList();
    }

    /**
     * An admin is an alum with a job, so this either attaches a credential to the
     * person who already holds that phone number, or creates the person first.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COORDINATORS, allEntries = true)
    public AdminAccountDto create(CreateAdminRequest request) {
        AdminSession actor = requireSuperAdmin();

        String username = request.username().strip();
        if (admins.existsByUsernameIgnoringCase(username)) {
            throw ApiException.conflict("username_taken",
                    "That username is already in use.", "এই ব্যবহারকারীর নামটি আগে থেকেই আছে।");
        }

        String phone = PhoneNumbers.normalize(request.phone());
        Person person = people.findByPhone(phone).orElseGet(Person::new);
        person.setName(request.name().strip());
        if (request.nameBn() != null && !request.nameBn().isBlank()) {
            person.setNameBn(request.nameBn().strip());
        }
        person.setPhone(phone);
        // A coordinator is somebody the committee has vouched for by definition.
        person.setStatus(PersonStatus.VERIFIED);
        people.save(person);

        if (admins.existsById(person.getId())) {
            throw ApiException.conflict("already_admin",
                    "That person is already an admin.", "এই ব্যক্তি ইতিমধ্যে অ্যাডমিন।");
        }

        AdminCredential credential = new AdminCredential();
        credential.setPerson(person);
        credential.setUsername(username);
        credential.setPasswordHash(passwordEncoder.encode(request.password()));
        credential.setRole(request.role());
        credential.setBatches(batchesFor(request.role(), request.batches()));
        credential.setActive(true);
        // They must set their own on first sign-in; the super admin knows this one.
        credential.setMustChange(true);
        credential.setCreatedBy(actor.personId());
        admins.save(credential);

        log.info("Admin {} created by {}", username, actor.username());
        return AdminAccountMapper.toDto(credential);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.COORDINATORS, allEntries = true)
    public AdminAccountDto update(UUID personId, UpdateAdminRequest request) {
        requireSuperAdmin();
        AdminCredential credential = load(personId);

        Person person = credential.getPerson();
        if (request.name() != null && !request.name().isBlank()) {
            person.setName(request.name().strip());
        }
        if (request.nameBn() != null) {
            person.setNameBn(request.nameBn().isBlank() ? null : request.nameBn().strip());
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            person.setPhone(PhoneNumbers.normalize(request.phone()));
        }
        people.save(person);

        if (request.batches() != null) {
            credential.setBatches(batchesFor(credential.getRole(), request.batches()));
        }
        if (request.active() != null) {
            credential.setActive(request.active());
        }
        admins.save(credential);

        // Their next request must see the new authority, not the cached one.
        context.invalidate(personId);
        return AdminAccountMapper.toDto(credential);
    }

    @Transactional
    public void setPassword(UUID personId, String password) {
        AdminSession actor = requireSuperAdmin();
        AdminCredential credential = load(personId);
        credential.setPasswordHash(passwordEncoder.encode(password));
        credential.setMustChange(true);
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        admins.save(credential);
        // Never logged, never echoed — the response is 204 and says nothing.
        log.info("Password for {} reset by {}", credential.getUsername(), actor.username());
    }

    /**
     * Removes the credential, not the person: they remain an alum with a
     * registration and a history, they simply stop being staff.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COORDINATORS, allEntries = true)
    public void revoke(UUID personId) {
        AdminSession actor = requireSuperAdmin();
        if (actor.personId().equals(personId)) {
            throw ApiException.badRequest("cannot_remove_self",
                    "You cannot remove your own admin access.", "আপনি নিজের অ্যাডমিন অ্যাক্সেস সরাতে পারবেন না।");
        }
        admins.delete(load(personId));
        context.invalidate(personId);
    }

    /** A super admin's authority is the absence of a scope, so it stores none. */
    private Set<Integer> batchesFor(AdminRole role, Set<Integer> requested) {
        return role == AdminRole.SUPER_ADMIN || requested == null ? Set.of() : Set.copyOf(requested);
    }

    private AdminCredential load(UUID personId) {
        return admins.findWithPersonByPersonId(personId).orElseThrow(() -> ApiException.notFound(
                "No such admin.", "এমন কোনো অ্যাডমিন নেই।"));
    }

    private AdminSession requireSuperAdmin() {
        AdminSession session = context.current();
        if (!session.isSuperAdmin()) {
            throw ApiException.forbidden(
                    "Only a super admin can do that.", "শুধু সুপার অ্যাডমিন এই কাজটি করতে পারেন।");
        }
        return session;
    }
}
