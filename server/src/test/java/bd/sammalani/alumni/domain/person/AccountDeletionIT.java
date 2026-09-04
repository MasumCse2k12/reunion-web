package bd.sammalani.alumni.domain.person;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What a member's own account deletion has to actually do to the database.
 * <p>
 * The first test is the one worth having. Scrubbing a person and then
 * tombstoning them looks like two ordinary writes, but Hibernate will not flush
 * pending updates to an entity it has been told to remove, and {@code @SoftDelete}
 * turns that removal into an {@code UPDATE} of one column — so the natural order
 * writes the tombstone and throws the cleared fields away. Nothing would fail:
 * the endpoint returns 204, the row disappears from the application, and the
 * member's phone number and date of birth sit in the table for good. Only a
 * query that looks past the tombstone can tell the two outcomes apart, which is
 * why this test reads through {@link JdbcTemplate} rather than the repository.
 * <p>
 * Runs against the compose Postgres on 5436 and skips itself when it is not up,
 * matching {@code SoftDeleteAuditIT}.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5436/alumni_test",
        "app.jwt.secret=test-secret-that-is-comfortably-over-thirty-two-bytes",
        "app.bootstrap.enabled=false"
})
@EnabledIf(value = "postgresIsUp", disabledReason = "needs the compose Postgres on 5436")
class AccountDeletionIT {

    @Autowired
    private AccountDeletionService deletion;
    @Autowired
    private PersonRepository people;
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
    void clean() {
        jdbc.execute("delete from payment where person_id in (select id from person where phone like '0172%' or name = 'Departing Member')");
        jdbc.execute("delete from registration where person_id in (select id from person where phone like '0172%' or name = 'Departing Member')");
        jdbc.execute("delete from audit_log");
        jdbc.execute("delete from person where phone like '0172%' or name = 'Departing Member'");
    }

    @Test
    @DisplayName("everything the member supplied is cleared, and the clearing survives the tombstone")
    void theScrubIsNotLostToTheTombstone() {
        UUID id = transactions.execute(status -> seedFullProfile().getId());

        transactions.executeWithoutResult(status -> deletion.delete(id));

        // Gone as far as every query in the application is concerned.
        assertThat(people.findById(id)).isEmpty();

        // Read past the tombstone: this is where a lost flush would show up.
        var row = jdbc.queryForMap("select * from person where id = ?", id);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("email")).isNull();
        assertThat(row.get("dob")).isNull();
        assertThat(row.get("gender")).isNull();
        assertThat(row.get("blood_group")).isNull();
        assertThat(row.get("occupation")).isNull();
        assertThat(row.get("city")).isNull();
        assertThat(row.get("photo_url")).isNull();
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("status")).isEqualTo("SEEDED");
        assertThat(row.get("extras").toString()).isEqualTo("{}");
    }

    @Test
    @DisplayName("the roster entry the school seeded is kept, so an admin can restore them")
    void nameAndBatchSurvive() {
        UUID id = transactions.execute(status -> seedFullProfile().getId());

        transactions.executeWithoutResult(status -> deletion.delete(id));

        var row = jdbc.queryForMap("select name, batch_year from person where id = ?", id);
        assertThat(row.get("name")).isEqualTo("Departing Member");
        assertThat(row.get("batch_year")).isEqualTo(2010);
    }

    @Test
    @DisplayName("the deleted number is free again, so the same person can come back")
    void thePhoneNumberIsReleased() {
        UUID id = transactions.execute(status -> seedFullProfile().getId());
        transactions.executeWithoutResult(status -> deletion.delete(id));

        // Would violate person_phone_uidx if the tombstone still held the number.
        UUID returning = transactions.execute(status -> {
            Person person = new Person();
            person.setName("Departing Member");
            person.setPhone("01722000001");
            person.setBatchYear(2010);
            return people.save(person).getId();
        });

        assertThat(returning).isNotEqualTo(id);
        assertThat(people.findByPhone("01722000001")).isPresent();
    }

    @Test
    @DisplayName("confirmed money stays in the ledger, flagged for refund and pointing at nobody")
    void confirmedPaymentsSurviveFlaggedForRefund() {
        UUID id = transactions.execute(status -> seedFullProfile().getId());
        UUID confirmed = insertPayment(id, "CONFIRMED", new BigDecimal("2000.00"), "BKASH", "TXN-CONFIRMED-1");
        UUID reported = insertPayment(id, "REPORTED", new BigDecimal("500.00"), "BKASH", "TXN-REPORTED-1");

        transactions.executeWithoutResult(status -> deletion.delete(id));

        // The row the committee's accounts depend on is still there, and still says
        // how much and against which bKash reference — the only handle left for a
        // refund, since the number to call them back on has just been deleted.
        var row = jdbc.queryForMap("select amount_bdt, reference, refund_pending, deleted_at from payment where id = ?", confirmed);
        assertThat(row.get("deleted_at")).isNull();
        assertThat((BigDecimal) row.get("amount_bdt")).isEqualByComparingTo("2000.00");
        assertThat(row.get("reference")).isEqualTo("TXN-CONFIRMED-1");
        assertThat(row.get("refund_pending")).isEqualTo(true);

        // Money the coordinator never accepted is not a refund anybody owes.
        assertThat(jdbc.queryForObject(
                "select refund_pending from payment where id = ?", Boolean.class, reported)).isFalse();

        // And the surviving row identifies nobody: person is soft-deleted, so the
        // EAGER association behind it no longer resolves.
        assertThat(people.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("the preview reports confirmed money only, before anything is deleted")
    void previewCountsConfirmedMoneyOnly() {
        UUID id = transactions.execute(status -> seedFullProfile().getId());
        insertPayment(id, "CONFIRMED", new BigDecimal("2000.00"), "BKASH", "TXN-CONFIRMED-2");
        insertPayment(id, "REPORTED", new BigDecimal("500.00"), "NAGAD", "TXN-REPORTED-2");

        var preview = deletion.preview(id);

        assertThat(preview.amountPaid()).isEqualByComparingTo("2000.00");
        assertThat(preview.refundPending()).isTrue();
        // Nothing was written by a preview.
        assertThat(people.findById(id)).isPresent();
    }

    @Test
    @DisplayName("a registration is cancelled, stripped of its guests and tombstoned")
    void theRegistrationGoesToo() {
        UUID id = transactions.execute(status -> seedFullProfile().getId());
        UUID registration = insertRegistration(id);

        transactions.executeWithoutResult(status -> deletion.delete(id));

        var row = jdbc.queryForMap(
                "select status, guests::text as guests, member_note, deleted_at from registration where id = ?",
                registration);
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(row.get("deleted_at")).isNotNull();
        // The guest list is other people's names on this member's row; it does not
        // get to survive behind a tombstone.
        assertThat(row.get("guests")).isEqualTo("[]");
        assertThat(row.get("member_note")).isNull();
    }

    /* ---------------- helpers ---------------- */

    private Person seedFullProfile() {
        Person person = new Person();
        person.setName("Departing Member");
        person.setNameBn("বিদায়ী সদস্য");
        person.setBatchYear(2010);
        person.setPhone("01722000001");
        person.setEmail("departing@example.com");
        person.setDob(java.time.LocalDate.of(1994, 3, 12));
        person.setGender(Gender.MALE);
        person.setBloodGroup("O+");
        person.setOccupation("Engineer");
        person.setCity("Dhaka");
        person.setStatus(PersonStatus.VERIFIED);
        person.setClaimedAt(java.time.Instant.now());
        person.getExtras().put("facebook", "fb.com/departing");
        // Left null on purpose: a non-null value would send StorageService at a
        // MinIO this test does not require to be running.
        person.setPhotoUrl(null);
        return people.save(person);
    }

    /** Against the event seeded by V2, which is the one the service looks up. */
    private UUID insertRegistration(UUID personId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into registration (id, event_id, person_id, batch_year, guests, member_note, status)
                values (?, (select id from event order by created_at limit 1), ?, 2010,
                        '[{"name":"A Guest","relation":"SPOUSE"}]'::jsonb, 'please seat us together', 'APPROVED')
                """, id, personId);
        return id;
    }

    private UUID insertPayment(UUID personId, String status, BigDecimal amount, String method, String reference) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into payment (id, person_id, purpose, amount_bdt, method, reference, status)
                values (?, ?, 'TICKET', ?, ?, ?, ?)
                """, id, personId, amount, method, reference, status);
        return id;
    }
}
