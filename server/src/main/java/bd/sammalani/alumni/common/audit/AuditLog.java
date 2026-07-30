package bd.sammalani.alumni.common.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One audit row, for reading only.
 * <p>
 * {@code @Immutable}, and it has no setters, because this mapping exists solely
 * so the admin portal can query the trail through the same repository layer as
 * everything else. Rows are written by {@link AuditTrail} with plain JDBC — an
 * entity that could be persisted here would fire the very events that produce
 * audit rows, and the first insert would not terminate.
 * <p>
 * A {@code long} identity key rather than a uuid: this is the only table in the
 * schema that grows without bound, and it is only ever appended to and read in
 * time order.
 */
@Entity
@Table(name = "audit_log")
@Immutable
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    /** The table name, not the Java type — see {@link EntityAuditListener}. */
    @Column(nullable = false)
    private String entity;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "batch_year")
    private Integer batchYear;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", nullable = false, length = 16)
    private ActorKind actorKind;

    @Column(name = "actor_label", nullable = false)
    private String actorLabel;

    private String note;

    /** {@code {"status": {"from": "SEEDED", "to": "VERIFIED"}}} — changed fields only. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Map<String, String>> changes;

    @Column(name = "request_id")
    private String requestId;

    private String ip;

    private String method;

    private String path;
}
