package bd.sammalani.alumni.admin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.AdminAccountDto;
import bd.sammalani.alumni.admin.AdminDtos.CreateAdminRequest;
import bd.sammalani.alumni.admin.AdminDtos.UpdateAdminRequest;
import bd.sammalani.alumni.common.audit.AuditAction;
import bd.sammalani.alumni.common.audit.AuditChange;
import bd.sammalani.alumni.common.audit.AuditTrail;
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

    /** Audit rows about a credential are keyed by username, as sign-in rows are. */
    private static final String SUBJECT = AdminLoginAttempts.SUBJECT;

    private final AdminRepository admins;
    private final PersonRepository people;
    private final PasswordEncoder passwordEncoder;
    private final AdminContextService context;
    private final AuditTrail trail;

    @Transactional(readOnly = true)
    public List<AdminAccountDto> list() {
        requireSuperAdmin();
        return admins.findAllWithPerson().stream().map(AdminAccountMapper::toDto).toList();
    }

    /**
     * An admin is an alum with a job, so this either attaches a credential to the
     * person who already holds that phone number, or creates the person first.
     * <p>
     * It also revives a revoked one. {@code admin_credential} is keyed on the
     * person, so once access has been withdrawn from somebody there is exactly one
     * row that can ever carry it again — and re-granting has to be that row coming
     * back rather than a second one being inserted. Without this branch, a
     * coordinator who stood down for a year could never be reinstated, and the
     * error would arrive as a primary key violation with nothing to explain it.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COORDINATORS, allEntries = true)
    public AdminAccountDto create(CreateAdminRequest request) {
        AdminSession actor = requireSuperAdmin();

        String username = request.username().strip();
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

        AdminCredential credential = admins.findWithPersonByPersonId(person.getId()).orElse(null);
        boolean reinstating = credential != null;
        if (reinstating && credential.isActive()) {
            throw ApiException.conflict("already_admin",
                    "That person is already an admin.", "এই ব্যক্তি ইতিমধ্যে অ্যাডমিন।");
        }
        // Their own current username is not a clash with themselves.
        if (!(reinstating && credential.getUsername().equalsIgnoreCase(username))
                && admins.existsByUsernameIgnoringCase(username)) {
            throw ApiException.conflict("username_taken",
                    "That username is already in use.", "এই ব্যবহারকারীর নামটি আগে থেকেই আছে।");
        }

        if (!reinstating) {
            credential = new AdminCredential();
            credential.setPerson(person);
            credential.setCreatedBy(actor.personId());
        }
        credential.setUsername(username);
        credential.setPasswordHash(passwordEncoder.encode(request.password()));
        credential.setRole(request.role());
        credential.setBatches(batchesFor(request.role(), request.batches()));
        credential.setActive(true);
        // They must set their own on first sign-in; the super admin knows this one.
        credential.setMustChange(true);
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        admins.save(credential);

        if (reinstating) {
            // created_by is left as it was: it records who first made this person a
            // coordinator, which is a different fact from who let them back in.
            trail.note("Admin access reinstated by " + actor.username());
            trail.record(AuditAction.ACCESS_RESTORED, SUBJECT, username, null);
            context.invalidate(person.getId());
        }

        log.info("Admin {} {} by {}", username, reinstating ? "reinstated" : "created", actor.username());
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
            Set<Integer> before = Set.copyOf(credential.getBatches());
            Set<Integer> after = batchesFor(credential.getRole(), request.batches());
            credential.setBatches(after);
            recordScopeChange(credential, before, after);
        }
        if (request.active() != null) {
            credential.setActive(request.active());
        }
        admins.save(credential);

        // Their next request must see the new authority, not the cached one.
        context.invalidate(personId);
        return AdminAccountMapper.toDto(credential);
    }

    /**
     * A batch assignment is an {@code @ElementCollection}, and changing one is a
     * collection event rather than an entity update — so the audit listener on the
     * flush never sees it. It is also the single most security-relevant write in
     * the application: it is the difference between a coordinator who may approve
     * 1974 and one who may not. Recorded by hand, here, for exactly that reason.
     */
    private void recordScopeChange(AdminCredential credential, Set<Integer> before, Set<Integer> after) {
        if (before.equals(after)) {
            return;
        }
        trail.record(AuditAction.SCOPE_CHANGED, SUBJECT, credential.getUsername(), null,
                Map.of("batches", new AuditChange(years(before), years(after))));
    }

    /** Sorted, so a diff of two assignments reads as a change and not as a reshuffle. */
    private static String years(Set<Integer> batches) {
        return batches.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
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

        // The entity update alone would record the hash as having changed, which is
        // true but reads as a data edit. A named action is what somebody scanning
        // for "who reset whose password" can actually filter on.
        trail.note("Password reset by " + actor.username());
        trail.record(AuditAction.PASSWORD_SET, SUBJECT, credential.getUsername(), null);

        // Never logged, never echoed — the response is 204 and says nothing.
        log.info("Password for {} reset by {}", credential.getUsername(), actor.username());
    }

    /**
     * Withdraws admin access. The person keeps their registration and their
     * history; they simply stop being staff.
     * <p>
     * <strong>This deactivates rather than deletes,</strong> and it is the one table
     * where that is done with a flag instead of a tombstone. {@code admin_credential}
     * is keyed on {@code person_id}, so a soft-deleted row would sit on that primary
     * key forever and make the person permanently un-re-appointable — and the
     * failure would surface as a key violation in {@link #create}, nowhere near
     * here. The {@code active} flag was already this table's tombstone: sign-in
     * refuses it, the session resolver 401s on it, and the coordinator list filters
     * it out. So it is used as one.
     * <p>
     * The visible consequence is that a revoked account stays in the list, marked
     * disabled, instead of disappearing. For an account that could approve
     * registrations and confirm money, that is the better of the two.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COORDINATORS, allEntries = true)
    public void revoke(UUID personId) {
        AdminSession actor = requireSuperAdmin();
        if (actor.personId().equals(personId)) {
            throw ApiException.badRequest("cannot_remove_self",
                    "You cannot remove your own admin access.", "আপনি নিজের অ্যাডমিন অ্যাক্সেস সরাতে পারবেন না।");
        }

        AdminCredential credential = load(personId);
        if (credential.isActive()) {
            credential.setActive(false);
            admins.save(credential);
            trail.note("Admin access revoked by " + actor.username());
            trail.record(AuditAction.ACCESS_REVOKED, SUBJECT, credential.getUsername(), null);
        }
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
