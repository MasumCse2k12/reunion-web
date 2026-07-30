package bd.sammalani.alumni.domain.referral;

import java.util.UUID;

import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

import bd.sammalani.alumni.common.audit.AuditBatchScoped;
import bd.sammalani.alumni.common.jpa.Auditable;
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
 * "I know this person" — a member handing the committee a lead on a classmate.
 * <p>
 * A dismissed lead is soft-deleted rather than removed, so that the same wrong
 * number arriving from four different people stays visible as four attempts
 * instead of looking like a fresh idea each time.
 */
@Entity
@Table(name = "referral")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class Referral extends Auditable implements AuditBatchScoped {

    @Id
    @GeneratedValue
    private UUID id;

    // EAGER because Person is soft-deleted; see the note on Person.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "referrer_id")
    private Person referrer;

    @Column(nullable = false)
    private String name;

    private String phone;

    @Column(name = "batch_year")
    private Integer batchYear;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReferralStatus status = ReferralStatus.NEW;

    @Column(name = "matched_person_id")
    private UUID matchedPersonId;

    @Override
    public Integer auditBatchYear() {
        return batchYear;
    }
}
