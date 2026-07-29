package bd.sammalani.alumni.domain.registration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import bd.sammalani.alumni.domain.event.TicketType;
import bd.sammalani.alumni.domain.payment.Payment;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.GuestDto;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.PaymentDto;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.RegistrationDto;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.ReviewDto;
import bd.sammalani.alumni.domain.review.Review;

/**
 * Entity to wire format, in one place so the member view and the admin view of a
 * registration cannot drift apart.
 */
public final class RegistrationMapper {

    private RegistrationMapper() {
    }

    public static RegistrationDto toDto(Registration r, Payment payment,
                                        Review memberReview, Review paymentReview,
                                        Map<String, TicketType> tickets) {
        return new RegistrationDto(
                r.getId(),
                r.getPerson().getId(),
                r.getBatchYear(),
                guests(r.getGuests(), tickets),
                r.getTshirtSize(),
                r.getFoodPref(),
                r.getMemberNote(),
                r.getAmountDue(),
                r.getStatus(),
                r.getPaymentStatus(),
                r.getSubmittedAt(),
                r.getQrToken(),
                review(memberReview),
                review(paymentReview),
                payment(payment));
    }

    public static List<GuestDto> guests(List<Guest> guests, Map<String, TicketType> tickets) {
        if (guests == null) {
            return List.of();
        }
        return guests.stream()
                .map(g -> new GuestDto(g.id(), g.name(), g.relation(), g.age(), g.ticketTypeCode(),
                        g.tshirtSize(), amountOf(tickets, g.ticketTypeCode())))
                .toList();
    }

    public static ReviewDto review(Review review) {
        return review == null ? null
                : new ReviewDto(review.getDecidedBy().getName(), review.getDecidedAt(), review.getNote());
    }

    public static PaymentDto payment(Payment payment) {
        return payment == null ? null
                : new PaymentDto(payment.getMethod(), payment.getReference(), payment.getAmountBdt(),
                payment.getReportedAt(), payment.getStatus());
    }

    private static BigDecimal amountOf(Map<String, TicketType> tickets, String code) {
        if (tickets == null || code == null) {
            return null;
        }
        TicketType ticket = tickets.get(code);
        return ticket == null ? null : ticket.getAmountBdt();
    }
}
