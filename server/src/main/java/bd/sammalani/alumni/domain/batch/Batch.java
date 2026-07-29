package bd.sammalani.alumni.domain.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An SSC passing year, 1968 through 2026. The year is the key; there is only ever one 1974. */
@Entity
@Table(name = "batch")
@Getter
@Setter
@NoArgsConstructor
public class Batch {

    @Id
    private Integer year;

    @Column(nullable = false)
    private String label;

    @Column(name = "label_bn")
    private String labelBn;

    /** What the school register says the batch size was — the denominator of "found so far". */
    @Column(name = "roster_estimate", nullable = false)
    private int rosterEstimate;

    @Column(name = "cover_url")
    private String coverUrl;
}
