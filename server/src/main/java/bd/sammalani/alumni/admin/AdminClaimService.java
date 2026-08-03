package bd.sammalani.alumni.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.ClaimDto;
import bd.sammalani.alumni.admin.AdminDtos.ClaimVerdict;
import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.common.web.CursorPage;
import bd.sammalani.alumni.common.web.Cursors;
import bd.sammalani.alumni.config.CacheConfig;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.person.PersonStatus;
import bd.sammalani.alumni.domain.registration.RegistrationMapper;
import bd.sammalani.alumni.domain.registration.RegistrationRepository;
import bd.sammalani.alumni.domain.review.Review;
import bd.sammalani.alumni.domain.review.ReviewDecision;
import bd.sammalani.alumni.domain.review.ReviewRepository;
import bd.sammalani.alumni.domain.review.ReviewSubjectType;
import lombok.RequiredArgsConstructor;

/**
 * Identity review: is this person really of the batch they say they are?
 * <p>
 * This is the queue the platform was missing. {@code PersonStatus} and
 * {@code RegistrationStatus} are separate on purpose — the platform is meant to
 * outlive one reunion, and a verified alum need not be coming to it — but the
 * only code that could ever move someone out of CLAIMED lived on the approval
 * of a registration. So anybody who claimed their name off the school register
 * and did not go on to register for the event was invisible to every admin
 * screen and stayed CLAIMED for good.
 * <p>
 * Scope is resolved from the session exactly as the application queue resolves
 * it, and for the same reason: a coordinator judges the batches they were given
 * and no others. {@link ReviewSubjectType#PERSON_VERIFICATION} has been in the
 * enum since the beginning waiting for this; every decision here writes one.
 */
@Service
@RequiredArgsConstructor
public class AdminClaimService {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    /** The statuses this queue can be filtered to — SEEDED is not a claim. */
    private static final Set<PersonStatus> READABLE =
            Set.of(PersonStatus.CLAIMED, PersonStatus.VERIFIED, PersonStatus.REJECTED);

    private final PersonRepository people;
    private final RegistrationRepository registrations;
    private final ReviewRepository reviews;
    private final AdminContextService context;

    /* ---------------- reading ---------------- */

    @Transactional(readOnly = true)
    public CursorPage<ClaimDto> page(PersonStatus status, Integer batchYear, String search,
                                     String cursor, Integer limit) {
        AdminSession admin = context.current();
        if (batchYear != null) {
            context.requireCovers(admin, batchYear);
        }

        PersonStatus wanted = status == null ? PersonStatus.CLAIMED : status;
        if (!READABLE.contains(wanted)) {
            throw ApiException.badRequest("unknown_status",
                    "That is not a claim status.", "এটি কোনো দাবির অবস্থা নয়।");
        }

        int pageSize = Cursors.clampLimit(limit, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        Cursors.Position position = Cursors.decode(cursor);
        boolean allBatches = admin.isSuperAdmin();
        Set<Integer> scope = admin.isSuperAdmin() ? Set.of() : admin.batches();
        String term = search == null || search.isBlank() ? null : search.strip();

        // One more than asked for: its existence is what says there is another
        // page, without a second count query per scroll.
        List<Person> rows = people.findClaimQueue(wanted, allBatches, scope,
                batchYear != null, batchYear, term,
                position != null,
                position == null ? null : position.submittedAt(),
                position == null ? null : position.id(),
                Limit.of(pageSize + 1));

        boolean hasMore = rows.size() > pageSize;
        List<Person> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

        String nextCursor = null;
        if (hasMore) {
            Person last = pageRows.getLast();
            nextCursor = Cursors.encode(last.getClaimedAt(), last.getId());
        }

        long total = people.countClaimQueue(wanted, allBatches, scope, batchYear != null, batchYear, term);
        return CursorPage.of(assemble(pageRows), nextCursor, total);
    }

    /* ---------------- deciding ---------------- */

    /**
     * Verify or refuse one claim.
     * <p>
     * A settled claim stays settled, on the same rule as the application queue:
     * the repeat is refused whoever asks, and only a super admin may decide the
     * other way. Verifying here does not approve anybody's registration — that
     * is a separate judgement about a separate thing, and the application queue
     * is still where it is made.
     */
    @Transactional
    @CacheEvict(value = {CacheConfig.ADMIN_STATS, CacheConfig.BATCH_COVERAGE}, allEntries = true)
    public ClaimDto decide(UUID personId, ClaimVerdict verdict, String note) {
        AdminSession admin = context.current();
        if (verdict == ClaimVerdict.REJECTED && (note == null || note.isBlank())) {
            throw ApiException.reasonRequired();
        }

        Person person = people.findById(personId).orElseThrow(() -> ApiException.notFound(
                "Person not found.", "এই ব্যক্তিকে খুঁজে পাওয়া যায়নি।"));
        context.requireCovers(admin, person.getBatchYear());

        if (person.getStatus() == PersonStatus.SEEDED) {
            throw ApiException.conflict("not_claimed",
                    "Nobody has claimed this name yet.", "এই নামটি এখনো কেউ দাবি করেননি।");
        }

        boolean verifying = verdict == ClaimVerdict.VERIFIED;
        PersonStatus settled = verifying ? PersonStatus.VERIFIED : PersonStatus.REJECTED;
        if (person.getStatus() == settled) {
            throw ApiException.conflict("already_decided", verifying
                    ? "This person is already verified." : "This claim is already rejected.", verifying
                    ? "এই ব্যক্তি আগেই যাচাই হয়েছেন।" : "এই দাবি আগেই বাতিল হয়েছে।");
        }
        if (person.getStatus() != PersonStatus.CLAIMED && !admin.isSuperAdmin()) {
            throw ApiException.conflict("already_decided",
                    "This claim has already been decided. A super admin can change it.",
                    "এই দাবির সিদ্ধান্ত আগেই হয়ে গেছে। সুপার অ্যাডমিন পরিবর্তন করতে পারবেন।");
        }

        person.setStatus(settled);
        people.save(person);

        reviews.save(Review.of(ReviewSubjectType.PERSON_VERIFICATION, person.getId(), person.getBatchYear(),
                verifying ? ReviewDecision.APPROVED : ReviewDecision.REJECTED, note,
                people.getReferenceById(admin.personId())));

        return assemble(List.of(person)).getFirst();
    }

    /* ---------------- assembly ---------------- */

    /**
     * Three queries for a page of any size: the people, who among them has a
     * submission behind their claim, and the last decision on each.
     */
    private List<ClaimDto> assemble(List<Person> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = page.stream().map(Person::getId).toList();
        Set<UUID> withSubmission = Set.copyOf(registrations.personIdsWithSubmission(ids));

        Map<UUID, Review> latest = new HashMap<>();
        for (Review review : reviews.findLatestForSubjects(ReviewSubjectType.PERSON_VERIFICATION, ids)) {
            latest.put(review.getSubjectId(), review);
        }

        return page.stream()
                .map(p -> new ClaimDto(p.getId(), p.getName(), p.getNameBn(), p.getBatchYear(),
                        p.getPhone(), p.getEmail(), p.getGender(), p.getOccupation(), p.getCity(),
                        p.getStatus(), claimedAt(p), withSubmission.contains(p.getId()),
                        RegistrationMapper.review(latest.get(p.getId()))))
                .toList();
    }

    /** Rows claimed before the column existed fall back to when they were created. */
    private static Instant claimedAt(Person person) {
        return person.getClaimedAt() != null ? person.getClaimedAt() : person.getCreatedAt();
    }
}
