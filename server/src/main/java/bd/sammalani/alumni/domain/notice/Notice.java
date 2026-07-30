package bd.sammalani.alumni.domain.notice;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

import bd.sammalani.alumni.common.jpa.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One item on the committee's notice board.
 * <p>
 * Soft-deleted like everything else, and here it is the mundane case that
 * justifies it: a notice taken down is a notice somebody will want back, or will
 * want to prove was once published — "the committee never announced the date" is
 * an argument that a tombstone settles and a DELETE does not.
 */
@Entity
@Table(name = "notice")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class Notice extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "title_bn")
    private String titleBn;

    private String body;

    @Column(name = "body_bn")
    private String bodyBn;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt = Instant.now();
}
