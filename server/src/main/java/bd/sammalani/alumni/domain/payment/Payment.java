package bd.sammalani.alumni.domain.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
 */
@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id")
    private Registration registration;

    @ManyToOne(fetch = FetchType.LAZY)
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_to_id")
    private Person paidTo;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.REPORTED;
}
