package bd.sammalani.alumni.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.ApplicationDto;
import bd.sammalani.alumni.admin.AdminDtos.BulkDecisionResponse;
import bd.sammalani.alumni.admin.AdminDtos.MemberStatus;
import bd.sammalani.alumni.admin.AdminDtos.MemberVerdict;
import bd.sammalani.alumni.admin.AdminDtos.PaymentVerdict;
import bd.sammalani.alumni.admin.AdminDtos.SkippedDto;
import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.common.web.CursorPage;
import bd.sammalani.alumni.common.web.Cursors;
import bd.sammalani.alumni.config.CacheConfig;
import bd.sammalani.alumni.domain.payment.Payment;
import bd.sammalani.alumni.domain.payment.PaymentRepository;
import bd.sammalani.alumni.domain.payment.PaymentStatus;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.person.PersonStatus;
import bd.sammalani.alumni.domain.registration.ApplicationQuery;
import bd.sammalani.alumni.domain.registration.Registration;
import bd.sammalani.alumni.domain.registration.RegistrationRepository;
import bd.sammalani.alumni.domain.registration.RegistrationStatus;
import bd.sammalani.alumni.domain.review.Review;
import bd.sammalani.alumni.domain.review.ReviewDecision;
import bd.sammalani.alumni.domain.review.ReviewRepository;
import bd.sammalani.alumni.domain.review.ReviewSubjectType;
import lombok.RequiredArgsConstructor;

/**
 * The review queue: what a coordinator sees, and every way they can decide it.
 * <p>
 * Single and bulk decisions run through the same {@link #resolve} guard, so the
 * scope check and the "no payment reported yet" rule cannot drift apart between
 * the two paths — the bug that is otherwise guaranteed the first time someone
 * adds a rule to one of them.
 */
@Service
@RequiredArgsConstructor
public class AdminQueueService {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    private final RegistrationRepository registrations;
    private final bd.sammalani.alumni.domain.batch.BatchRepository batches;
    private final PaymentRepository payments;
    private final PersonRepository people;
    private final ReviewRepository reviews;
    private final AdminContextService context;
    private final ApplicationAssembler assembler;

    /* ---------------- reading ---------------- */

    @Transactional(readOnly = true)
    public CursorPage<ApplicationDto> page(MemberStatus memberStatus, PaymentStatus paymentStatus,
                                           Integer batchYear, String search, String cursor, Integer limit) {
        AdminSession admin = context.current();
        // A filter may narrow the caller's authority; it can never widen it.
        if (batchYear != null) {
            context.requireCovers(admin, batchYear);
        }

        int pageSize = Cursors.clampLimit(limit, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        Cursors.Position position = Cursors.decode(cursor);

        ApplicationQuery query = new ApplicationQuery(
                toRegistrationStatus(memberStatus),
                paymentStatus,
                batchYear,
                search,
                admin.scopeOrNull(),
                position == null ? null : position.submittedAt(),
                position == null ? null : position.id());

        // One more row than asked for: its existence is what tells us there is a
        // next page, without a second count query per scroll.
        List<Registration> rows = registrations.findPage(query, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<Registration> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

        String nextCursor = null;
        if (hasMore) {
            Registration last = pageRows.getLast();
            nextCursor = Cursors.encode(last.getSubmittedAt(), last.getId());
        }

        return CursorPage.of(assembler.assemble(pageRows), nextCursor, registrations.count(query));
    }

    @Transactional(readOnly = true)
    public ApplicationDto one(UUID id) {
        AdminSession admin = context.current();
        Registration registration = registrations.findWithDetailsById(id)
                .orElseThrow(AdminQueueService::notFound);
        context.requireCovers(admin, registration.getBatchYear());
        return assembler.assembleOne(registration);
    }

    /** The years this admin may filter by — all 59 for a super admin. */
    @Transactional(readOnly = true)
    public List<Integer> batchYearsForCurrentAdmin() {
        AdminSession admin = context.current();
        if (admin.isSuperAdmin()) {
            return batches.findAllByOrderByYearAsc().stream().map(b -> b.getYear()).toList();
        }
        return admin.batches().stream().sorted().toList();
    }

    /* ---------------- deciding: one ---------------- */

    @Transactional
    @CacheEvict(value = {CacheConfig.ADMIN_STATS, CacheConfig.BATCH_COVERAGE}, allEntries = true)
    public ApplicationDto decideMember(UUID id, MemberVerdict verdict, String note) {
        AdminSession admin = context.current();
        requireReason(verdict == MemberVerdict.REJECTED, note);

        Resolution resolution = resolve(admin, id, null);
        if (resolution.skipped() != null) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "not_decidable",
                    resolution.skipped().reason(), resolution.skipped().reasonBn());
        }

        applyMemberDecision(admin, resolution.registration(), verdict, note);
        return assembler.assembleOne(resolution.registration());
    }

    @Transactional
    @CacheEvict(value = CacheConfig.ADMIN_STATS, allEntries = true)
    public ApplicationDto decidePayment(UUID id, PaymentVerdict verdict, String note) {
        AdminSession admin = context.current();
        requireReason(verdict == PaymentVerdict.REJECTED, note);

        Resolution resolution = resolve(admin, id, verdict);
        if (resolution.skipped() != null) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "not_decidable",
                    resolution.skipped().reason(), resolution.skipped().reasonBn());
        }

        applyPaymentDecision(admin, resolution.registration(), verdict, note);
        return assembler.assembleOne(resolution.registration());
    }

    /* ---------------- deciding: many ---------------- */

    /**
     * One verdict and one note, applied to many applications.
     * <p>
     * The note is written onto every row, which is exactly why a bulk rejection
     * demands a reason as loudly as a single one: forty people told "declined"
     * with no explanation is worse than one, not better.
     */
    @Transactional
    @CacheEvict(value = {CacheConfig.ADMIN_STATS, CacheConfig.BATCH_COVERAGE}, allEntries = true)
    public BulkDecisionResponse decideMembers(List<UUID> ids, MemberVerdict verdict, String note) {
        AdminSession admin = context.current();
        requireReason(verdict == MemberVerdict.REJECTED, note);

        List<Registration> updated = new ArrayList<>();
        List<SkippedDto> skipped = new ArrayList<>();

        for (UUID id : ids) {
            Resolution resolution = resolve(admin, id, null);
            if (resolution.skipped() != null) {
                skipped.add(resolution.skipped());
                continue;
            }
            applyMemberDecision(admin, resolution.registration(), verdict, note);
            updated.add(resolution.registration());
        }

        return new BulkDecisionResponse(assembler.assemble(updated), skipped);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.ADMIN_STATS, allEntries = true)
    public BulkDecisionResponse decidePayments(List<UUID> ids, PaymentVerdict verdict, String note) {
        AdminSession admin = context.current();
        requireReason(verdict == PaymentVerdict.REJECTED, note);

        List<Registration> updated = new ArrayList<>();
        List<SkippedDto> skipped = new ArrayList<>();

        for (UUID id : ids) {
            Resolution resolution = resolve(admin, id, verdict);
            if (resolution.skipped() != null) {
                skipped.add(resolution.skipped());
                continue;
            }
            applyPaymentDecision(admin, resolution.registration(), verdict, note);
            updated.add(resolution.registration());
        }

        return new BulkDecisionResponse(assembler.assemble(updated), skipped);
    }

    /* ---------------- the shared guard ---------------- */

    /**
     * Resolve one id for this admin, or explain why it cannot be decided.
     *
     * @param paymentVerdict non-null when this is a payment decision, so that
     *                       confirming a payment nobody reported is caught here
     *                       rather than in two separate call sites
     */
    private Resolution resolve(AdminSession admin, UUID id, PaymentVerdict paymentVerdict) {
        Registration registration = registrations.findWithDetailsById(id).orElse(null);
        if (registration == null) {
            return Resolution.skip(new SkippedDto(id, id.toString(),
                    "Application not found", "আবেদনটি পাওয়া যায়নি"));
        }
        String name = registration.getPerson().getName();

        if (!admin.covers(registration.getBatchYear())) {
            return Resolution.skip(new SkippedDto(id, name,
                    "This batch is outside your assignment.", "এই ব্যাচটি আপনার দায়িত্বের বাইরে।"));
        }
        if (registration.getStatus() == RegistrationStatus.DRAFT) {
            return Resolution.skip(new SkippedDto(id, name,
                    "This has not been submitted yet.", "এটি এখনো জমা দেওয়া হয়নি।"));
        }
        if (paymentVerdict == PaymentVerdict.CONFIRMED && latestPayment(registration) == null) {
            return Resolution.skip(new SkippedDto(id, name,
                    "The member has not reported a payment yet.", "সদস্য এখনো পেমেন্টের তথ্য দেননি।"));
        }
        return Resolution.of(registration);
    }

    /* ---------------- applying ---------------- */

    private void applyMemberDecision(AdminSession admin, Registration registration, MemberVerdict verdict, String note) {
        boolean approved = verdict == MemberVerdict.APPROVED;
        registration.setStatus(approved ? RegistrationStatus.APPROVED : RegistrationStatus.REJECTED);
        if (approved && registration.getQrToken() == null) {
            // Issued on approval, not on payment: the seat is confirmed by a
            // coordinator's judgement, and the money follows offline.
            registration.setQrToken(UUID.randomUUID().toString());
        }

        // The person's own verification travels with the decision. They are
        // separate states on purpose — a verified alum need not be coming — but
        // approving someone's registration is also saying they are who they say.
        Person person = registration.getPerson();
        if (approved) {
            person.setStatus(PersonStatus.VERIFIED);
        } else if (person.getStatus() == PersonStatus.SEEDED) {
            person.setStatus(PersonStatus.REJECTED);
        }
        people.save(person);
        registrations.save(registration);

        reviews.save(Review.of(ReviewSubjectType.REGISTRATION, registration.getId(), registration.getBatchYear(),
                approved ? ReviewDecision.APPROVED : ReviewDecision.REJECTED, note, decider(admin)));
    }

    private void applyPaymentDecision(AdminSession admin, Registration registration, PaymentVerdict verdict, String note) {
        boolean confirmed = verdict == PaymentVerdict.CONFIRMED;
        Payment payment = latestPayment(registration);

        if (payment != null) {
            payment.setStatus(confirmed ? PaymentStatus.CONFIRMED : PaymentStatus.REJECTED);
            payments.save(payment);
            reviews.save(Review.of(ReviewSubjectType.PAYMENT, payment.getId(), registration.getBatchYear(),
                    confirmed ? ReviewDecision.CONFIRMED : ReviewDecision.REJECTED, note, decider(admin)));
        }

        // Rejecting a claim returns the registration to UNPAID when there was
        // nothing to reject, so the member can report the right reference.
        registration.setPaymentStatus(confirmed ? PaymentStatus.CONFIRMED
                : payment == null ? PaymentStatus.UNPAID : PaymentStatus.REJECTED);
        registrations.save(registration);
    }

    private Payment latestPayment(Registration registration) {
        return payments.findFirstByRegistrationIdOrderByReportedAtDesc(registration.getId()).orElse(null);
    }

    /** A reference, not a select: the decision only needs the foreign key. */
    private Person decider(AdminSession admin) {
        return people.getReferenceById(admin.personId());
    }

    private void requireReason(boolean isRejection, String note) {
        if (isRejection && (note == null || note.isBlank())) {
            throw ApiException.reasonRequired();
        }
    }

    private static RegistrationStatus toRegistrationStatus(MemberStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> RegistrationStatus.SUBMITTED;
            case APPROVED -> RegistrationStatus.APPROVED;
            case REJECTED -> RegistrationStatus.REJECTED;
        };
    }

    private static ApiException notFound() {
        return ApiException.notFound("Application not found", "আবেদনটি পাওয়া যায়নি");
    }

    /** Either a registration this admin may decide, or the reason they may not. */
    private record Resolution(Registration registration, SkippedDto skipped) {

        static Resolution of(Registration registration) {
            return new Resolution(registration, null);
        }

        static Resolution skip(SkippedDto skipped) {
            return new Resolution(null, skipped);
        }
    }
}
