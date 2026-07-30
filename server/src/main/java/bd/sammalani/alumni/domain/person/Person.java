package bd.sammalani.alumni.domain.person;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.type.SqlTypes;

import bd.sammalani.alumni.common.audit.AuditBatchScoped;
import bd.sammalani.alumni.common.jpa.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A human the school knows about — which is not the same thing as a user.
 * <p>
 * Most rows here will never log in: they are typed in off a 1974 register by a
 * volunteer, or they are teachers, or they have died. Authentication attaches to
 * a person; it does not define one. That is why there is no password column
 * (see {@code AdminCredential}) and why {@code phone} is nullable.
 * <p>
 * Never deleted. A removal sets {@code deleted_at} and Hibernate adds
 * {@code deleted_at is null} to every query that touches this table, including
 * the joins from {@code registration} and {@code review}. Which means the "right
 * to be removed" in §7 of the design doc is one call to {@code delete} rather
 * than an audit of every read in the codebase — and it is reversible, which
 * matters because the people asking to be removed are the same people who will
 * ask to come back once their batch group starts posting photographs.
 * <p>
 * <strong>Every to-one pointing here is EAGER, and not by preference.</strong>
 * Hibernate refuses to map a lazy {@code @ManyToOne} or {@code @OneToOne} to a
 * soft-deleted entity, and it is right to: a proxy is built from the foreign key
 * alone, so it cannot know the row it stands for is tombstoned, and the first
 * getter call would have to either resurrect a deleted person or throw from
 * somewhere far away from the query that asked. The same applies to
 * {@code Registration} and any other entity that gains {@code @SoftDelete}. The
 * cost is small here because every read path that walks one of these already
 * fetches it — see the entity graphs on the repositories and the explicit
 * {@code fetch} in {@code ApplicationQueryRepositoryImpl}. Add the join when you
 * add the query; do not go back to LAZY, it will not start the application.
 */
@Entity
@Table(name = "person")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class Person extends Auditable implements AuditBatchScoped {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_bn")
    private String nameBn;

    /** SSC passing year. Null for teachers and staff, who have no batch. */
    @Column(name = "batch_year")
    private Integer batchYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PersonStatus status = PersonStatus.SEEDED;

    /** Normalised to 11 digits (01XXXXXXXXX) before it ever reaches here. */
    @Column(unique = true, length = 16)
    private String phone;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Gender gender;

    private LocalDate dob;

    @Column(name = "blood_group", length = 4)
    private String bloodGroup;

    private String occupation;

    private String city;

    @Column(nullable = false)
    private boolean deceased = false;

    /**
     * The forty fields the committee will ask for in month three, without a
     * migration each time. Promote one to a real column when you need to index
     * it, not before.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> extras = new LinkedHashMap<>();

    /** Set when this person is folded into another; never delete a duplicate. */
    @Column(name = "merged_into_id")
    private UUID mergedIntoId;

    public String displayName() {
        return nameBn != null && !nameBn.isBlank() ? nameBn : name;
    }

    @Override
    public Integer auditBatchYear() {
        return batchYear;
    }
}
