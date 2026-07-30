package bd.sammalani.alumni.domain.registration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.type.SqlTypes;

import bd.sammalani.alumni.common.audit.AuditBatchScoped;
import bd.sammalani.alumni.common.jpa.Auditable;
import bd.sammalani.alumni.domain.event.Event;
import bd.sammalani.alumni.domain.event.TicketType;
import bd.sammalani.alumni.domain.payment.PaymentStatus;
import bd.sammalani.alumni.domain.person.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One member's submission for one event, including whoever they are bringing.
 * <p>
 * Two fields here are copies of data that lives elsewhere, and both are
 * deliberate. {@code batchYear} is copied from the person so the review queue
 * filters and pages on a single index without joining, and {@code paymentStatus}
 * is maintained from the payment rows in the same transaction for the same
 * reason. The payment table remains the source of truth about money.
 * <p>
 * Soft-deleted, and the uniqueness rules were rewritten for it: the index behind
 * {@code (event_id, person_id)} is partial on {@code deleted_at is null}, so a
 * withdrawn registration does not lock that member out of ever registering
 * again. That failure would have surfaced as a bug in the claim flow, three
 * screens away from its cause.
 */
@Entity
@Table(name = "registration")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class Registration extends Auditable implements AuditBatchScoped {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // EAGER because Person is soft-deleted; see the note on Person. Every read of
    // a registration renders a name anyway, so the queue already joins this.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "batch_year")
    private Integer batchYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Guest> guests = new ArrayList<>();

    @Column(name = "tshirt_size", length = 8)
    private String tshirtSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_pref", length = 16)
    private FoodPreference foodPref;

    /** Whatever the member wanted their coordinator to know. */
    @Column(name = "member_note")
    private String memberNote;

    @Column(name = "amount_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RegistrationStatus status = RegistrationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Issued when the registration is approved — not when money arrives. */
    @Column(name = "qr_token", unique = true)
    private String qrToken;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    /** A member may still edit while nobody has judged it, or after a rejection. */
    public boolean editableByMember() {
        return status == RegistrationStatus.DRAFT || status == RegistrationStatus.REJECTED;
    }

    @Override
    public Integer auditBatchYear() {
        return batchYear;
    }
}
