package bd.sammalani.alumni.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import bd.sammalani.alumni.domain.person.PersonStatus;

class AuditChangesTest {

    private static final String[] NAMES = {"name", "status", "phone", "updatedAt"};

    @Test
    @DisplayName("an update records the fields that changed and nothing else")
    void recordsOnlyWhatChanged() {
        String[] before = {"Rafiqul Islam", "SEEDED", null, "2027-01-01T00:00:00Z"};
        String[] after = {"Rafiqul Islam", "VERIFIED", "01712345678", "2027-01-02T00:00:00Z"};

        Map<String, AuditChange> changes = AuditChanges.ofUpdate(NAMES, before, after, new int[]{0, 1, 2, 3});

        assertThat(changes).containsOnlyKeys("status", "phone");
        assertThat(changes.get("status")).isEqualTo(new AuditChange("SEEDED", "VERIFIED"));
        assertThat(changes.get("phone")).isEqualTo(new AuditChange(null, "01712345678"));
    }

    /**
     * Every {@code save} bumps {@code updatedAt}, so if this leaked into the diff
     * the trail would be mostly rows saying that something was saved and nothing
     * changed — and it would stop being read.
     */
    @Test
    @DisplayName("a save that only moved updated_at produces no audit row at all")
    void bookkeepingAloneIsNotAnEvent() {
        String[] before = {"Rafiqul Islam", "VERIFIED", "01712345678", "2027-01-01T00:00:00Z"};
        String[] after = {"Rafiqul Islam", "VERIFIED", "01712345678", "2027-06-30T00:00:00Z"};

        assertThat(AuditChanges.ofUpdate(NAMES, before, after, null)).isEmpty();
    }

    @Test
    @DisplayName("when Hibernate cannot say what is dirty, every field is compared")
    void nullDirtyListComparesEverything() {
        String[] before = {"Rafiq", "SEEDED", null, null};
        String[] after = {"Rafiqul Islam", "SEEDED", null, null};

        assertThat(AuditChanges.ofUpdate(NAMES, before, after, null))
                .containsOnlyKeys("name");
    }

    /**
     * The single most important rule here. These rows are never deleted and are
     * readable through the admin portal, so a hash written into one is a worse leak
     * than the one auditing exists to catch.
     */
    @Test
    @DisplayName("a password hash is recorded as having changed, never as a value")
    void neverRecordsASecret() {
        String[] names = {"username", "passwordHash", "qrToken"};
        String[] before = {"rafiqul", "$argon2id$v=19$m=16384,t=2,p=1$OLD", null};
        String[] after = {"rafiqul", "$argon2id$v=19$m=16384,t=2,p=1$NEW", "9f0c-ticket-token"};

        Map<String, AuditChange> changes = AuditChanges.ofUpdate(names, before, after, null);

        assertThat(changes.get("passwordHash")).isEqualTo(new AuditChange("«set»", "«set»"));
        assertThat(changes.get("qrToken")).isEqualTo(new AuditChange("«unset»", "«set»"));
        assertThat(changes.toString()).doesNotContain("argon2id", "ticket-token");
    }

    @Test
    @DisplayName("an insert lists what was filled in, not the forty fields that were not")
    void insertSkipsNulls() {
        Map<String, AuditChange> changes = AuditChanges.ofInsert(NAMES,
                new String[]{"Rafiqul Islam", "SEEDED", null, "2027-01-01T00:00:00Z"});

        assertThat(changes).containsOnlyKeys("name", "status");
        assertThat(changes.get("name").from()).isNull();
        assertThat(changes.get("name").to()).isEqualTo("Rafiqul Islam");
    }

    @Test
    @DisplayName("a delete keeps the last state of the row, which is what a restore is judged against")
    void deleteKeepsThePriorState() {
        Map<String, AuditChange> changes = AuditChanges.ofDelete(NAMES,
                new String[]{"Rafiqul Islam", "VERIFIED", "01712345678", null});

        assertThat(changes).containsOnlyKeys("name", "status", "phone");
        assertThat(changes.get("phone")).isEqualTo(new AuditChange("01712345678", null));
    }

    /**
     * {@code person.extras} is an open jsonb bag and {@code member_note} is free
     * text. Two full copies of either, on every write, is how a log table becomes
     * the largest thing in the nightly backup.
     */
    @Test
    @DisplayName("a long value is truncated rather than copied twice into the trail")
    void truncatesLongValues() {
        String long_ = "ক".repeat(1_000);

        String rendered = AuditChanges.render(long_);

        assertThat(rendered).hasSize(AuditChanges.MAX_LENGTH + 1).endsWith("…");
    }

    @Test
    @DisplayName("values render as the stable text a human will read years later")
    void rendersValuesAsText() {
        assertThat(AuditChanges.render(null)).isNull();
        assertThat(AuditChanges.render(PersonStatus.VERIFIED)).isEqualTo("VERIFIED");
        assertThat(AuditChanges.render(Instant.parse("2027-02-12T09:30:15Z"))).isEqualTo("2027-02-12T09:30:15Z");
        assertThat(AuditChanges.render(new java.math.BigDecimal("1500.00"))).isEqualTo("1500.00");
        assertThat(AuditChanges.render(List.of("SPOUSE", "CHILD"))).isEqualTo("[SPOUSE, CHILD]");
        assertThat(AuditChanges.render(Map.of("occupation", "teacher"))).isEqualTo("{occupation=teacher}");

        UUID id = UUID.randomUUID();
        assertThat(AuditChanges.render(id)).isEqualTo(id.toString());
    }

    /** A byte array's toString is its identity hash, which tells a reader nothing. */
    @Test
    @DisplayName("binary is described, not dumped")
    void describesBinary() {
        assertThat(AuditChanges.render(new byte[24])).isEqualTo("«24 bytes»");
    }
}
