package bd.sammalani.alumni.domain.admin;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import bd.sammalani.alumni.common.jpa.Auditable;
import bd.sammalani.alumni.domain.person.Person;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Password authentication, which only admins have. Members log in with a code
 * sent to their phone and never have a row here.
 * <p>
 * The primary key is the person's — an admin <em>is</em> an alum who has been
 * given a job, not a separate species of user.
 */
@Entity
@Table(name = "admin_credential")
@Getter
@Setter
@NoArgsConstructor
public class AdminCredential extends Auditable {

    @Id
    @Column(name = "person_id")
    private UUID personId;

    // EAGER because Person is soft-deleted; see the note on Person. Sharing the
    // primary key means this is the same row lookup either way.
    @MapsId
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(nullable = false)
    private String username;

    /** Argon2id. Never selected into a DTO, never logged, never returned. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AdminRole role = AdminRole.GROUP_ADMIN;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "must_change", nullable = false)
    private boolean mustChange = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * The batch years this admin may act on. Empty for a SUPER_ADMIN, whose
     * authority is the absence of a scope rather than a row for all 59 years.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "admin_batch_scope", joinColumns = @JoinColumn(name = "person_id"))
    @Column(name = "batch_year", nullable = false)
    private Set<Integer> batches = new LinkedHashSet<>();

    public boolean isSuperAdmin() {
        return role == AdminRole.SUPER_ADMIN;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /** A super admin covers everything; a group admin covers exactly its assigned years. */
    public boolean covers(Integer batchYear) {
        return isSuperAdmin() || (batchYear != null && batches.contains(batchYear));
    }
}
