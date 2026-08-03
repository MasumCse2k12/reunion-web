package bd.sammalani.alumni.domain.person;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The identity queue's read, against a real Postgres.
 * <p>
 * Mocks cannot say whether this query is correct: the whole of it is nullable
 * bind parameters, an {@code in} list that must mean "nothing" when it is empty,
 * and a keyset comparison over two columns. Every one of those fails by
 * returning the wrong rows rather than by throwing, which is exactly the kind of
 * bug a coordinator would report as "I can see 1998 and I am not meant to".
 * <p>
 * Runs on its own database and skips itself when nothing is listening, in the
 * same way as {@code SoftDeleteAuditIT}.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5436/alumni_test",
        "app.jwt.secret=test-secret-that-is-comfortably-over-thirty-two-bytes",
        "app.bootstrap.enabled=false"
})
@EnabledIf(value = "postgresIsUp", disabledReason = "needs the compose Postgres on 5436")
class ClaimQueueIT {

    private static final String TEST_PHONE_PREFIX = "0199";

    @Autowired
    private PersonRepository people;
    @Autowired
    private JdbcTemplate jdbc;

    public static boolean postgresIsUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5436), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void clearTestRows() {
        jdbc.execute("delete from person where phone like '" + TEST_PHONE_PREFIX + "%'");
    }

    @Test
    @DisplayName("a group admin sees claims from their batches and nobody else's")
    void scopeIsAuthorityNotAFilter() {
        claim("Rahim, 2010", 2010, TEST_PHONE_PREFIX + "0000001", PersonStatus.CLAIMED, minutesAgo(3));
        claim("Karim, 1998", 1998, TEST_PHONE_PREFIX + "0000002", PersonStatus.CLAIMED, minutesAgo(2));

        List<Person> mine = query(false, Set.of(2010), null, null);

        assertThat(mine).extracting(Person::getName).containsExactly("Rahim, 2010");
    }

    @Test
    @DisplayName("a group admin with no batches sees nothing, not everything")
    void emptyScopeMatchesNothing() {
        claim("Rahim, 2010", 2010, TEST_PHONE_PREFIX + "0000003", PersonStatus.CLAIMED, minutesAgo(1));

        assertThat(query(false, Set.of(), null, null)).isEmpty();
        // The same call as a super admin returns it, so the emptiness is the scope.
        assertThat(query(true, Set.of(), null, null)).isNotEmpty();
    }

    @Test
    @DisplayName("only claims waiting on somebody come back by default")
    void verifiedAndSeededAreNotInTheQueue() {
        claim("Waiting", 2010, TEST_PHONE_PREFIX + "0000004", PersonStatus.CLAIMED, minutesAgo(3));
        claim("Already verified", 2010, TEST_PHONE_PREFIX + "0000005", PersonStatus.VERIFIED, minutesAgo(2));
        claim("Never claimed", 2010, TEST_PHONE_PREFIX + "0000006", PersonStatus.SEEDED, null);

        assertThat(query(true, Set.of(), null, null)).extracting(Person::getName).containsExactly("Waiting");
    }

    @Test
    @DisplayName("search matches either script or the number, and narrows within the scope")
    void searchNarrows() {
        claim("Rahim Uddin", 2010, TEST_PHONE_PREFIX + "0000007", PersonStatus.CLAIMED, minutesAgo(3));
        claim("Karim Mia", 2010, TEST_PHONE_PREFIX + "0000008", PersonStatus.CLAIMED, minutesAgo(2));

        assertThat(query(true, Set.of(), null, "rahim")).extracting(Person::getName).containsExactly("Rahim Uddin");
        assertThat(query(true, Set.of(), null, "0000008")).extracting(Person::getName).containsExactly("Karim Mia");
    }

    @Test
    @DisplayName("the keyset walks every row exactly once, newest first")
    void keysetPagesWithoutSkippingOrRepeating() {
        Instant base = minutesAgo(10);
        for (int i = 0; i < 5; i++) {
            claim("Claimant " + i, 2010, TEST_PHONE_PREFIX + "001000" + i, PersonStatus.CLAIMED,
                    base.plus(i, ChronoUnit.MINUTES));
        }

        List<Person> firstPage = people.findClaimQueue(PersonStatus.CLAIMED, true, Set.of(), false, null, null,
                false, null, null, Limit.of(2));
        Person last = firstPage.getLast();
        List<Person> secondPage = people.findClaimQueue(PersonStatus.CLAIMED, true, Set.of(), false, null, null,
                true, last.getClaimedAt(), last.getId(), Limit.of(2));

        assertThat(firstPage).extracting(Person::getName).containsExactly("Claimant 4", "Claimant 3");
        assertThat(secondPage).extracting(Person::getName).containsExactly("Claimant 2", "Claimant 1");
        assertThat(people.countClaimQueue(PersonStatus.CLAIMED, true, Set.of(), false, null, null)).isEqualTo(5);
    }

    @Test
    @DisplayName("the batch filter narrows the scope it is given")
    void batchFilterNarrows() {
        claim("Rahim, 2010", 2010, TEST_PHONE_PREFIX + "0000009", PersonStatus.CLAIMED, minutesAgo(3));
        claim("Karim, 2011", 2011, TEST_PHONE_PREFIX + "0000010", PersonStatus.CLAIMED, minutesAgo(2));

        assertThat(query(false, Set.of(2010, 2011), 2011, null))
                .extracting(Person::getName).containsExactly("Karim, 2011");
    }

    private List<Person> query(boolean allBatches, Set<Integer> scope, Integer batchYear, String q) {
        return people.findClaimQueue(PersonStatus.CLAIMED, allBatches, scope, batchYear != null, batchYear, q,
                false, null, null, Limit.of(50));
    }

    private static Instant minutesAgo(int minutes) {
        return Instant.now().minus(minutes, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
    }

    private void claim(String name, int batchYear, String phone, PersonStatus status, Instant claimedAt) {
        Person person = new Person();
        person.setName(name);
        person.setBatchYear(batchYear);
        person.setPhone(phone);
        person.setStatus(status);
        person.setClaimedAt(claimedAt);
        people.saveAndFlush(person);
    }
}
