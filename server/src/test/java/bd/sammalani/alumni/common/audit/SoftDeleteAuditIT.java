package bd.sammalani.alumni.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.person.PersonStatus;

/**
 * The half of this feature that cannot be unit-tested: whether Postgres and
 * Hibernate actually behave the way the design assumes they do.
 * <p>
 * Three assumptions in particular, and every one of them would fail silently —
 * the application would start, the endpoints would answer, and the trail would
 * simply be missing rows nobody thought to look for:
 * <ul>
 *   <li>that {@code @SoftDelete} still fires {@code POST_DELETE}, rather than
 *       turning the delete into an update the audit listener sees as an entity
 *       change with no mapped field to diff, and therefore skips;</li>
 *   <li>that the audit row lands in the same transaction as the change, and
 *       disappears with it on a rollback;</li>
 *   <li>that a tombstone stops holding its unique index entry, so a removed
 *       member's mobile number can be used again.</li>
 * </ul>
 * <p>
 * It runs against a real Postgres — a soft delete is a database behaviour and an
 * in-memory H2 would prove nothing about it — but on its own database, not the
 * dev one, and it skips itself when there is nothing listening. Nothing in CI
 * runs it; a laptop with the compose stack up does.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5436/alumni_test",
        "app.jwt.secret=test-secret-that-is-comfortably-over-thirty-two-bytes",
        // Nothing here needs the first super admin, and creating one would put
        // rows in the trail that the assertions would have to skip past.
        "app.bootstrap.enabled=false"
})
// JUnit's own @EnabledIf, not Spring's: this has to be decided before the
// application context is built, and Spring's evaluates the expression against a
// context that would already have failed to start.
@EnabledIf(value = "postgresIsUp", disabledReason = "needs the compose Postgres on 5436")
class SoftDeleteAuditIT {

    @Autowired
    private PersonRepository people;
    @Autowired
    private Tombstones tombstones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;

    public static boolean postgresIsUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5436), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void clearTheTrail() {
        // Truncated rather than filtered around, so every assertion below can talk
        // about "the rows for this person" without an ordering assumption.
        jdbc.execute("delete from audit_log");
        jdbc.execute("delete from person where phone like '0171%'");
    }

    @Test
    @DisplayName("an insert, an update and a soft delete each leave exactly one audit row")
    void everyWriteIsRecorded() {
        UUID id = transactions.execute(status -> seed("Rafiqul Islam", "01711000001").getId());

        assertThat(actions(id)).containsExactly("INSERT");
        assertThat(changesOf(id, "INSERT")).hasEntrySatisfying("name", change ->
                assertThat(change).containsEntry("from", null).containsEntry("to", "Rafiqul Islam"));
        assertThat(changesOf(id, "INSERT")).hasEntrySatisfying("status", change ->
                assertThat(change).containsEntry("to", "SEEDED"));

        transactions.executeWithoutResult(status -> {
            Person person = people.findById(id).orElseThrow();
            person.setStatus(PersonStatus.VERIFIED);
            person.setCity("Narail");
            people.save(person);
        });

        assertThat(actions(id)).containsExactly("INSERT", "UPDATE");
        Map<String, Map<String, String>> update = changesOf(id, "UPDATE");
        assertThat(update).containsEntry("status", Map.of("from", "SEEDED", "to", "VERIFIED"));
        assertThat(update).hasEntrySatisfying("city", change ->
                assertThat(change).containsEntry("from", null).containsEntry("to", "Narail"));
        // updated_at moved too, and recording it would be noise
        assertThat(update).doesNotContainKey("updatedAt");
    }

    /**
     * The assumption the whole design rests on. If {@code @SoftDelete} were to stop
     * firing {@code POST_DELETE}, deletions would vanish from the trail while
     * everything else kept working.
     */
    @Test
    @DisplayName("delete tombstones the row instead of removing it, and says so in the trail")
    void deleteIsSoftAndAudited() {
        UUID id = transactions.execute(status -> seed("Shahida Begum", "01711000002").getId());

        transactions.executeWithoutResult(status -> people.delete(people.findById(id).orElseThrow()));

        // Gone as far as the application is concerned...
        assertThat(people.findById(id)).isEmpty();
        // ...but still there, with a timestamp on it.
        assertThat(jdbc.queryForObject(
                "select deleted_at is not null from person where id = ?", Boolean.class, id)).isTrue();

        assertThat(actions(id)).containsExactly("INSERT", "DELETE");
        // The last state of the row, which is what a restore gets judged against.
        assertThat(changesOf(id, "DELETE")).hasEntrySatisfying("name", change ->
                assertThat(change).containsEntry("from", "Shahida Begum").containsEntry("to", null));
    }

    @Test
    @DisplayName("a tombstone stops reserving the mobile number it was holding")
    void aTombstoneReleasesItsUniqueIndexEntry() {
        UUID first = transactions.execute(status -> seed("Wrong Record", "01711000003").getId());
        transactions.executeWithoutResult(status -> people.delete(people.findById(first).orElseThrow()));

        // The partial unique index excludes tombstones, so this must not conflict.
        UUID second = transactions.execute(status -> seed("Right Record", "01711000003").getId());

        assertThat(second).isNotEqualTo(first);
        assertThat(people.findByPhone("01711000003")).get()
                .extracting(Person::getName).isEqualTo("Right Record");
    }

    @Test
    @DisplayName("restore clears the tombstone and is itself recorded")
    void restoreIsAuditedInItsOwnRight() {
        UUID id = transactions.execute(status -> seed("Returning Member", "01711000004").getId());
        transactions.executeWithoutResult(status -> people.delete(people.findById(id).orElseThrow()));

        assertThat(tombstones.list(TombstoneKind.PERSON, 10))
                .anySatisfy(row -> {
                    assertThat(row.id()).isEqualTo(id);
                    assertThat(row.label()).isEqualTo("Returning Member");
                    // Taken from the audit trail, not from a column on person.
                    assertThat(row.deletedBy()).isNotNull();
                });

        transactions.executeWithoutResult(status -> {
            assertThat(tombstones.restore(TombstoneKind.PERSON, id)).isTrue();
        });

        assertThat(people.findById(id)).isPresent();
        assertThat(tombstones.restore(TombstoneKind.PERSON, id)).isFalse();
    }

    /**
     * The property that makes the trail worth believing. An audit row on its own
     * connection would survive a rollback and accuse somebody of a change that
     * never happened.
     */
    @Test
    @DisplayName("a rolled back change leaves no audit row behind")
    void nothingIsRecordedForAChangeThatDidNotHappen() {
        UUID id = transactions.execute(status -> seed("Rolled Back", "01711000005").getId());
        jdbc.execute("delete from audit_log");

        transactions.executeWithoutResult(status -> {
            Person person = people.findById(id).orElseThrow();
            person.setCity("Dhaka");
            people.save(person);
            status.setRollbackOnly();
        });

        assertThat(actions(id)).isEmpty();
        assertThat(jdbc.queryForObject("select city from person where id = ?", String.class, id)).isNull();
    }

    /* ---------------- helpers ---------------- */

    private Person seed(String name, String phone) {
        Person person = new Person();
        person.setName(name);
        person.setPhone(phone);
        person.setBatchYear(2010);
        person.setStatus(PersonStatus.SEEDED);
        return people.save(person);
    }

    private List<String> actions(UUID entityId) {
        return jdbc.queryForList(
                "select action from audit_log where entity = 'person' and entity_id = ? order by id",
                String.class, entityId.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> changesOf(UUID entityId, String action) {
        String json = jdbc.queryForObject(
                "select changes::text from audit_log where entity = 'person' and entity_id = ? and action = ?",
                String.class, entityId.toString(), action);
        return JsonMapper.builder().build().readValue(json, Map.class);
    }
}
