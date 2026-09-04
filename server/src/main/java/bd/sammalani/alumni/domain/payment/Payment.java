package bd.sammalani.alumni.domain.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

import bd.sammalani.alumni.common.audit.AuditBatchScoped;
import bd.sammalani.alumni.common.jpa.Auditable;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.registration.Registration;
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
 * Money that moved outside this system, recorded after the fact.
 * <p>
 * A row here is a <em>claim</em>: the member says they handed 1,500 taka to
 * their coordinator and gives a bKash TrxID. It asserts nothing about whether
 * the money arrived. Only a coordinator's CONFIRMED decision does that.
 * <p>
 * Soft-deleted, and of all the tables this one has the least business ever losing
 * a row: a claim about money that has disappeared from the database is the
 * accusation the committee cannot answer. The partial unique index on
 * {@code (method, reference)} excludes tombstones, so withdrawing a claim frees
 * the transaction number again — the same reason a rejected claim frees it.
 */
@Entity
@Table(name = "payment")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends Auditable implements AuditBatchScoped {

    @Id
    @GeneratedValue
    private UUID id;

    // All three to-ones on this entity are EAGER because their targets are
    // soft-deleted and Hibernate will not map a lazy one; see the note on Person.
    // The queue reads a page of payments at a time, so they are join-fetched
    // together in findLatestForRegistrations rather than a select per row.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registration_id")
    private Registration registration;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "person_id")
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentPurpose purpose = PaymentPurpose.TICKET;

    @Column(name = "amount_bdt", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountBdt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PaymentMethod method;

    /** TrxID, bank slip number, or the coordinator's receipt book number. */
    private String reference;

    /** The coordinator the member says they paid. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paid_to_id")
    private Person paidTo;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt = Instant.now();

    /**
     * Set when the payer deleted their account while this money was confirmed.
     * The row outlives them deliberately; this is the marker a coordinator
     * filters on when the ex-member phones up asking for it back, matched on
     * {@link #reference} because there is no longer a name or number to match on.
     */
    @Column(name = "refund_pending", nullable = false)
    private boolean refundPending = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.REPORTED;

    /**
     * A payment has no batch year of its own; it borrows its registration's, and
     * only when that registration is already loaded.
     * <p>
     * The guard is the whole method. This is called from inside a Hibernate flush,
     * where {@code registration} is very often an uninitialised proxy, and touching
     * a getter on it would issue a SELECT in the middle of writing — the classic way
     * an audit hook turns into a deadlock or a {@code ConcurrentModificationException}
     * on the action queue. Every path that decides a payment has the registration
     * in hand anyway, so in practice the year is there; when it is not, an
     * unattributed year makes the row super-admin-only, which is the safe way to
     * be wrong.
     */
    @Override
    public Integer auditBatchYear() {
        return registration != null && Hibernate.isInitialized(registration)
                ? registration.getBatchYear()
                : null;
    }
}
