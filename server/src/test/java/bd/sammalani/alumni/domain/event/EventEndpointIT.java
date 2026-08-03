package bd.sammalani.alumni.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The ticket-price route, over real HTTP.
 * <p>
 * Worth an integration test rather than a unit one because the two things that
 * could be wrong about it are both outside the service: whether the route is
 * reachable without a token — somebody asks the price before they have proved a
 * phone number — and whether the amounts survive the hop out of the database as
 * numbers the app can add up. Both would pass every mock and fail in a browser.
 * <p>
 * A real server rather than MockMvc, deliberately: MockMvc would skip the very
 * filter chain the first assertion is about. It runs against the same test
 * database as the other ITs and skips itself when nothing is listening.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:postgresql://localhost:5436/alumni_test",
                "app.jwt.secret=test-secret-that-is-comfortably-over-thirty-two-bytes",
                "app.bootstrap.enabled=false"
        })
@EnabledIf(value = "postgresIsUp", disabledReason = "needs the compose Postgres on 5436")
class EventEndpointIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    /** The app is served under /smbc; a test that forgot it would 404 on everything. */
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public static boolean postgresIsUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5436), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("anybody can read the reunion and what a seat costs")
    void currentEventIsPublic() {
        JsonNode body = get("/api/v1/events/current");

        assertThat(body.get("slug").asString()).isEqualTo("reunion-2027");

        JsonNode tickets = body.get("ticketTypes");
        assertThat(tickets.size()).isEqualTo(5);
        // Display order is the committee's, taken from sort_order.
        assertThat(tickets.get(0).get("code").asString()).isEqualTo("ALUMNI");
        assertThat(tickets.get(0).get("amount").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("1500.00"));
        assertThat(tickets.get(1).get("code").asString()).isEqualTo("SPOUSE");
        assertThat(tickets.get(1).get("relation").asString()).isEqualTo("SPOUSE");
        // The member's own ticket covers no guest relation.
        assertThat(tickets.get(0).get("relation").isNull()).isTrue();
    }

    @Test
    @DisplayName("the reunion itself comes back, dates included, in a form a browser can read")
    void eventDetailsAreUsable() {
        JsonNode body = get("/api/v1/events/current");

        assertThat(body.get("title").asString()).isEqualTo("Grand Reunion 2027");
        assertThat(body.get("titleBn").asString()).isEqualTo("মহা পুনর্মিলনী ২০২৭");
        assertThat(body.get("venue").asString()).isEqualTo("School Campus, Chalitatala, Narail");
        assertThat(body.get("status").asString()).isEqualTo("OPEN");

        // The landing page counts down to this and the dashboard prints it, both
        // through `new Date(...)`. An epoch number would satisfy every Java test
        // here and give the browser an unparseable date, so assert the shape.
        JsonNode startsAt = body.get("startsAt");
        assertThat(startsAt.isString()).isTrue();
        assertThat(java.time.Instant.parse(startsAt.asString()))
                .isEqualTo(java.time.Instant.parse("2027-03-11T03:00:00Z"));
    }

    @Test
    @DisplayName("a literal path is not swallowed by the slug template")
    void currentIsNotReadAsASlug() {
        // /current and /{slug} sit on the same segment; if the template won, the
        // first test would be asking for an event named "current".
        assertThat(get("/api/v1/events/reunion-2027").get("slug").asString()).isEqualTo("reunion-2027");
    }

    @Test
    @DisplayName("an unknown slug is a 404, not an empty event")
    void unknownSlugIsNotFound() {
        assertThat(statusOf("/api/v1/events/reunion-1999")).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private JsonNode get(String path) {
        return JSON.readTree(client().get().uri(path).retrieve().body(String.class));
    }

    private HttpStatusCode statusOf(String path) {
        return client().get().uri(path)
                .exchange((request, response) -> response.getStatusCode(), false);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port + contextPath);
    }
}
