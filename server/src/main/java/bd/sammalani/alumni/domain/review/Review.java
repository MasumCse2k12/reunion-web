package bd.sammalani.alumni.domain.review;

import java.time.Instant;
import java.util.UUID;

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
 * One human decision: "is this really a 1974 alum?" or "did this money arrive?"
 * <p>
 * Append-only. A reversal is a new row, never an update, because the question
 * that gets asked later is not "what is the state" but "who decided this, when,
 * and were they allowed to?" {@code batchYear} is denormalised onto the decision
 * so that authority is checkable from this row alone, without joining back
 * through a person whose batch may since have been corrected.
 * <p>
 * A bulk decision writes one row per subject — forty approvals are forty rows,
 * indistinguishable in the audit trail from forty separate clicks. That is the
 * point of the design, not an inefficiency in it.
 */
@Entity
@Table(name = "review")
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 32)
    private ReviewSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "batch_year")
    private Integer batchYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewDecision decision;

    /** Mandatory when the decision is REJECTED — enforced here, in the service, and by a CHECK. */
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by", nullable = false)
    private Person decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt = Instant.now();

    public static Review of(ReviewSubjectType type, UUID subjectId, Integer batchYear,
                            ReviewDecision decision, String note, Person decidedBy) {
        Review review = new Review();
        review.subjectType = type;
        review.subjectId = subjectId;
        review.batchYear = batchYear;
        review.decision = decision;
        review.note = note == null || note.isBlank() ? null : note.strip();
        review.decidedBy = decidedBy;
        review.decidedAt = Instant.now();
        return review;
    }
}
