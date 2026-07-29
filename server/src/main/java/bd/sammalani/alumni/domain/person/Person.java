package bd.sammalani.alumni.domain.person;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 */
@Entity
@Table(name = "person")
@Getter
@Setter
@NoArgsConstructor
public class Person extends Auditable {

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
}
