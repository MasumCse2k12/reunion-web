package bd.sammalani.alumni.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.ApplicationDto;
import bd.sammalani.alumni.admin.AdminDtos.BulkDecisionResponse;
import bd.sammalani.alumni.admin.AdminDtos.MemberStatus;
import bd.sammalani.alumni.admin.AdminDtos.MemberVerdict;
import bd.sammalani.alumni.admin.AdminDtos.PaymentVerdict;
import bd.sammalani.alumni.admin.AdminDtos.QueueKind;
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
 * <p>
 * A decision is once. Re-deciding a settled row is refused for everyone, and
 * <em>changing</em> a settled row is a super admin's call: a coordinator who
 * mis-tapped Reject needs a way back, but it must not be the same button that
 * a bulk select-all can sweep over a whole batch. The ordinary way back from a
 * member rejection is the member's own — {@code editableByMember()} lets them
 * correct and resubmit, which returns the row to the queue on its own.
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
    public CursorPage<ApplicationDto> page(QueueKind kind, MemberStatus memberStatus, PaymentStatus paymentStatus,
                                           Integer batchYear, String search, String cursor, Integer limit) {
        AdminSession admin = context.current();
        // A filter may narrow the caller's authority; it can never widen it.
        if (batchYear != null) {
            context.requireCovers(admin, batchYear);
        }

        int pageSize = Cursors.clampLimit(limit, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        Cursors.Position position = Cursors.decode(cursor);

        ApplicationQuery query = new ApplicationQuery(
                memberStatusFor(kind, memberStatus),
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

        Resolution resolution = resolve(admin, id, verdict, null);
        resolution.throwIfSkipped();

        applyMemberDecision(admin, resolution.registration(), verdict, note);
        return assembler.assembleOne(resolution.registration());
    }

    @Transactional
    @CacheEvict(value = CacheConfig.ADMIN_STATS, allEntries = true)
    public ApplicationDto decidePayment(UUID id, PaymentVerdict verdict, String note) {
        AdminSession admin = context.current();
        requireReason(verdict == PaymentVerdict.REJECTED, note);

        Resolution resolution = resolve(admin, id, null, verdict);
        resolution.throwIfSkipped();

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
            Resolution resolution = resolve(admin, id, verdict, null);
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
            Resolution resolution = resolve(admin, id, null, verdict);
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
     * <p>
     * Exactly one of the two verdicts is non-null, and which one says what kind
     * of decision this is. Everything both kinds share — the row exists, it is
     * inside the caller's scope, it has actually been submitted — is checked
     * once here, before either branch.
     */
    private Resolution resolve(AdminSession admin, UUID id, MemberVerdict memberVerdict, PaymentVerdict paymentVerdict) {
        Registration registration = registrations.findWithDetailsById(id).orElse(null);
        if (registration == null) {
            return Resolution.skip(HttpStatus.NOT_FOUND, new SkippedDto(id, id.toString(),
                    "Application not found", "আবেদনটি পাওয়া যায়নি"));
        }
        String name = registration.getPerson().getName();

        if (!admin.covers(registration.getBatchYear())) {
            return Resolution.skip(HttpStatus.FORBIDDEN, new SkippedDto(id, name,
                    "This batch is outside your assignment.", "এই ব্যাচটি আপনার দায়িত্বের বাইরে।"));
        }
        if (registration.getStatus() == RegistrationStatus.DRAFT) {
            return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(id, name,
                    "This has not been submitted yet.", "এটি এখনো জমা দেওয়া হয়নি।"));
        }

        return memberVerdict != null
                ? resolveMember(admin, registration, name, memberVerdict)
                : resolvePayment(admin, registration, name, paymentVerdict);
    }

    /**
     * A member decision stands unless a super admin overturns it.
     * <p>
     * The repeat and the reversal are separated on purpose. Deciding the same
     * way twice is never anything but a mistake — a stale screen, or a
     * select-all over a filter showing settled rows — so it is refused whoever
     * asks. Deciding the other way is a real correction, and it needs an
     * authority that a batch coordinator working a queue does not carry.
     */
    private Resolution resolveMember(AdminSession admin, Registration registration, String name, MemberVerdict verdict) {
        RegistrationStatus status = registration.getStatus();
        if (status == RegistrationStatus.SUBMITTED) {
            return Resolution.of(registration);
        }
        if (status == RegistrationStatus.CANCELLED) {
            return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(registration.getId(), name,
                    "The member withdrew this registration.", "সদস্য এই নিবন্ধনটি প্রত্যাহার করেছেন।"));
        }

        boolean approved = status == RegistrationStatus.APPROVED;
        if (approved == (verdict == MemberVerdict.APPROVED)) {
            return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(registration.getId(), name, approved
                    ? "This member is already approved." : "This member is already rejected.", approved
                    ? "এই সদস্য আগেই অনুমোদিত হয়েছেন।" : "এই সদস্য আগেই বাতিল হয়েছেন।"));
        }
        if (!admin.isSuperAdmin()) {
            return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(registration.getId(), name,
                    "This has already been decided. A super admin can change it.",
                    "এটির সিদ্ধান্ত আগেই হয়ে গেছে। সুপার অ্যাডমিন পরিবর্তন করতে পারবেন।"));
        }
        return Resolution.of(registration);
    }

    /**
     * Confirmed money is settled. Rejected money is not: the member re-reports
     * and the row returns as REPORTED on its own, so only the repeat is refused
     * there. Reversing a confirmation is a super admin's call — it is a claim
     * about cash that a coordinator has already reconciled against a statement.
     */
    private Resolution resolvePayment(AdminSession admin, Registration registration, String name, PaymentVerdict verdict) {
        boolean confirming = verdict == PaymentVerdict.CONFIRMED;
        Payment payment = latestPayment(registration);
        PaymentStatus status = registration.getPaymentStatus();

        // Money follows the seat. Confirming a payment for a membership nobody
        // has granted — or one that was refused — books cash against a place at
        // the event that does not exist, and the member has a receipt to argue
        // with. Rejection stays open either way, so a wrong claim against an
        // unapproved registration can still be cleared.
        if (confirming && registration.getStatus() != RegistrationStatus.APPROVED) {
            return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(registration.getId(), name,
                    "Approve this member before confirming their payment.",
                    "পেমেন্ট নিশ্চিত করার আগে সদস্যকে অনুমোদন করুন।"));
        }
        if (confirming && payment == null) {
            return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(registration.getId(), name,
                    "The member has not reported a payment yet.", "সদস্য এখনো পেমেন্টের তথ্য দেননি।"));
        }
        if (status == PaymentStatus.CONFIRMED) {
            if (confirming) {
                return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(registration.getId(), name,
                        "This payment is already confirmed.", "এই পেমেন্ট আগেই নিশ্চিত হয়েছে।"));
            }
            if (!admin.isSuperAdmin()) {
                return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(registration.getId(), name,
                        "This payment is already confirmed. A super admin can reverse it.",
                        "এই পেমেন্ট আগেই নিশ্চিত হয়েছে। সুপার অ্যাডমিন এটি বাতিল করতে পারবেন।"));
            }
            return Resolution.of(registration);
        }
        if (!confirming && status == PaymentStatus.REJECTED) {
            return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(registration.getId(), name,
                    "This payment is already rejected.", "এই পেমেন্ট আগেই বাতিল হয়েছে।"));
        }
        // Nothing reported and nothing recorded: rejecting is a no-op, not a
        // reset. The reset below still applies when the two have drifted apart.
        if (!confirming && payment == null && status == PaymentStatus.UNPAID) {
            return Resolution.skip(HttpStatus.CONFLICT, new SkippedDto(registration.getId(), name,
                    "There is no payment to reject.", "বাতিল করার মতো কোনো পেমেন্ট নেই।"));
        }
        return Resolution.of(registration);
    }

    /* ---------------- applying ---------------- */

    private void applyMemberDecision(AdminSession admin, Registration registration, MemberVerdict verdict, String note) {
        boolean approved = verdict == MemberVerdict.APPROVED;
        registration.setStatus(approved ? RegistrationStatus.APPROVED : RegistrationStatus.REJECTED);
        if (approved) {
            if (registration.getQrToken() == null) {
                // Issued on approval, not on payment: the seat is confirmed by a
                // coordinator's judgement, and the money follows offline.
                registration.setQrToken(UUID.randomUUID().toString());
            }
        } else {
            // Withdrawn with the approval that issued it. A rejection that left
            // the pass alive would be a rejection only in the database: the
            // member still walks through the gate on a token the queue believes
            // it has taken back.
            registration.setQrToken(null);
        }

        // The person's own verification travels with the decision. They are
        // separate states on purpose — a verified alum need not be coming — but
        // approving someone's registration is also saying they are who they say.
        //
        // A rejection only reaches back to the person while nothing else has
        // vouched for them. Someone already VERIFIED — by a coordinator on an
        // earlier registration, or by holding an admin account — stays so: a
        // seat refused for this event says nothing about which batch they sat in.
        Person person = registration.getPerson();
        if (approved) {
            person.setStatus(PersonStatus.VERIFIED);
        } else if (person.getStatus() == PersonStatus.SEEDED || person.getStatus() == PersonStatus.CLAIMED) {
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

    /**
     * The payments queue is approved members only, and that is the server's
     * decision rather than the caller's: a {@code memberStatus} sent with it is
     * overridden, not honoured. Anything else would put a Confirm button next to
     * someone whose membership is still pending or was refused, and the money
     * queue exists to collect from people who have a seat.
     */
    private static RegistrationStatus memberStatusFor(QueueKind kind, MemberStatus requested) {
        if (kind == QueueKind.PAYMENTS) {
            return RegistrationStatus.APPROVED;
        }
        if (requested == null) {
            return null;
        }
        return switch (requested) {
            case PENDING -> RegistrationStatus.SUBMITTED;
            case APPROVED -> RegistrationStatus.APPROVED;
            case REJECTED -> RegistrationStatus.REJECTED;
        };
    }

    private static ApiException notFound() {
        return ApiException.notFound("Application not found", "আবেদনটি পাওয়া যায়নি");
    }

    /**
     * Either a registration this admin may decide, or the reason they may not.
     * <p>
     * The status rides along because the bulk paths swallow a refusal into
     * {@code skipped} while the single paths have to turn it into a response
     * code, and "outside your batches" and "already approved" are not the same
     * answer to the caller.
     */
    private record Resolution(Registration registration, SkippedDto skipped, HttpStatus status) {

        static Resolution of(Registration registration) {
            return new Resolution(registration, null, null);
        }

        static Resolution skip(HttpStatus status, SkippedDto skipped) {
            return new Resolution(null, skipped, status);
        }

        void throwIfSkipped() {
            if (skipped != null) {
                throw new ApiException(status, "not_decidable", skipped.reason(), skipped.reasonBn());
            }
        }
    }
}
