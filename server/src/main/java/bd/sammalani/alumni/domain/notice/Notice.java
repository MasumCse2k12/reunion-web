package bd.sammalani.alumni.domain.notice;

import java.time.Instant;
import java.util.UUID;

import bd.sammalani.alumni.common.jpa.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notice")
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
