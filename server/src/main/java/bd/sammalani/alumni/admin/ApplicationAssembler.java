package bd.sammalani.alumni.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.admin.AdminDtos.ApplicationDto;
import bd.sammalani.alumni.admin.AdminDtos.MemberStatus;
import bd.sammalani.alumni.domain.event.EventService;
import bd.sammalani.alumni.domain.event.TicketType;
import bd.sammalani.alumni.domain.payment.Payment;
import bd.sammalani.alumni.domain.payment.PaymentRepository;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.registration.Registration;
import bd.sammalani.alumni.domain.registration.RegistrationMapper;
import bd.sammalani.alumni.domain.registration.RegistrationStatus;
import bd.sammalani.alumni.domain.review.Review;
import bd.sammalani.alumni.domain.review.ReviewRepository;
import bd.sammalani.alumni.domain.review.ReviewSubjectType;
import lombok.RequiredArgsConstructor;

/**
 * Turns a page of registrations into what a coordinator reads.
 * <p>
 * The whole point of this class is that it does so in a fixed number of
 * queries regardless of page size: one for the registrations (person already
 * fetched), one for the latest payment claim of every row, one for the latest
 * decision on every registration, and one for the latest decision on every
 * payment. A page of ten and a page of two hundred cost the same four
 * round trips.
 */
@Component
@RequiredArgsConstructor
public class ApplicationAssembler {

    private final PaymentRepository payments;
    private final ReviewRepository reviews;
    private final EventService events;

    @Transactional(readOnly = true)
    public List<ApplicationDto> assemble(List<Registration> page) {
        if (page.isEmpty()) {
            return List.of();
        }

        List<UUID> registrationIds = page.stream().map(Registration::getId).toList();

        Map<UUID, Payment> latestPayment = new HashMap<>();
        for (Payment payment : payments.findLatestForRegistrations(registrationIds)) {
            latestPayment.put(payment.getRegistration().getId(), payment);
        }

        Map<UUID, Review> memberReviews = latestReviews(ReviewSubjectType.REGISTRATION, registrationIds);
        Map<UUID, Review> paymentReviews = latestReviews(ReviewSubjectType.PAYMENT,
                latestPayment.values().stream().map(Payment::getId).toList());

        Map<String, TicketType> tickets = events.current().getTicketTypes().stream()
                .collect(java.util.stream.Collectors.toMap(TicketType::getCode, Function.identity()));

        return page.stream()
                .map(r -> {
                    Payment payment = latestPayment.get(r.getId());
                    Review paymentReview = payment == null ? null : paymentReviews.get(payment.getId());
                    return toDto(r, payment, memberReviews.get(r.getId()), paymentReview, tickets);
                })
                .toList();
    }

    public ApplicationDto toDto(Registration r, Payment payment, Review memberReview, Review paymentReview,
                                Map<String, TicketType> tickets) {
        Person p = r.getPerson();
        return new ApplicationDto(
                r.getId(), p.getId(), p.getName(), p.getNameBn(), r.getBatchYear(),
                p.getPhone(), p.getEmail(), p.getGender(), p.getDob(), p.getBloodGroup(),
                p.getOccupation(), p.getCity(),
                RegistrationMapper.guests(r.getGuests(), tickets),
                r.getAmountDue(), r.getSubmittedAt(), r.getMemberNote(),
                memberStatusOf(r.getStatus()),
                RegistrationMapper.review(memberReview),
                r.getPaymentStatus(),
                RegistrationMapper.payment(payment),
                RegistrationMapper.review(paymentReview));
    }

    /** Convenience for the single-row paths, which have no page to batch with. */
    @Transactional(readOnly = true)
    public ApplicationDto assembleOne(Registration registration) {
        return assemble(List.of(registration)).getFirst();
    }

    /**
     * Sent in but not yet judged reads as PENDING to the portal. CANCELLED is
     * folded onto REJECTED because the queue offers no third button, and a
     * cancelled registration is not awaiting anybody.
     */
    private MemberStatus memberStatusOf(RegistrationStatus status) {
        return switch (status) {
            case APPROVED -> MemberStatus.APPROVED;
            case REJECTED, CANCELLED -> MemberStatus.REJECTED;
            case SUBMITTED, DRAFT -> MemberStatus.PENDING;
        };
    }

    private Map<UUID, Review> latestReviews(ReviewSubjectType type, List<UUID> subjectIds) {
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Review> bySubject = new HashMap<>();
        for (Review review : reviews.findLatestForSubjects(type, subjectIds)) {
            bySubject.put(review.getSubjectId(), review);
        }
        return bySubject;
    }
}
