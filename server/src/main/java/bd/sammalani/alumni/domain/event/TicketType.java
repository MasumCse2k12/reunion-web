package bd.sammalani.alumni.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

import bd.sammalani.alumni.domain.registration.GuestRelation;
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
 * What a seat costs. Prices live in the database, not in a constant: the
 * committee changes them, and a price change must never need a deployment.
 */
@Entity
@Table(name = "ticket_type")
@Getter
@Setter
@NoArgsConstructor
public class TicketType {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** Stable identifier the app refers to: ALUMNI, SPOUSE, CHILD, CHILD_FREE, GUEST. */
    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_bn")
    private String nameBn;

    private String note;

    @Column(name = "note_bn")
    private String noteBn;

    @Column(name = "amount_bdt", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountBdt = BigDecimal.ZERO;

    /** Which guest relation this ticket covers. Null means it is the member's own. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private GuestRelation relation;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
