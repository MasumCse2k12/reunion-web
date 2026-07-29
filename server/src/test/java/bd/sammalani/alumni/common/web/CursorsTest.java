package bd.sammalani.alumni.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import bd.sammalani.alumni.common.error.ApiException;

class CursorsTest {

    @Test
    @DisplayName("a cursor round-trips to the exact instant and id it encoded")
    void roundTrips() {
        Instant at = Instant.parse("2027-02-12T09:30:15.123456Z");
        UUID id = UUID.randomUUID();

        Cursors.Position decoded = Cursors.decode(Cursors.encode(at, id));

        assertThat(decoded).isNotNull();
        assertThat(decoded.submittedAt()).isEqualTo(at);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    @DisplayName("microsecond precision survives, because Postgres stores it")
    void keepsMicroseconds() {
        Instant at = Instant.ofEpochSecond(1_800_000_000L, 999_999_000);

        assertThat(Cursors.decode(Cursors.encode(at, UUID.randomUUID())).submittedAt()).isEqualTo(at);
    }

    /**
     * A bookmarked page from last week should not be a 400 in someone's face —
     * it should quietly start from the top.
     */
    @Test
    @DisplayName("junk decodes to null rather than throwing")
    void junkIsHarmless() {
        assertThat(Cursors.decode(null)).isNull();
        assertThat(Cursors.decode("")).isNull();
        assertThat(Cursors.decode("not-base64!!")).isNull();
        assertThat(Cursors.decode("bm90LWEtY3Vyc29y")).isNull();          // "not-a-cursor"
        assertThat(Cursors.decode("MTIzNDU2Om5vdC1hLXV1aWQ")).isNull();   // "123456:not-a-uuid"
    }

    @Test
    @DisplayName("nothing to encode means no next page")
    void nullsEncodeToNull() {
        assertThat(Cursors.encode(null, UUID.randomUUID())).isNull();
        assertThat(Cursors.encode(Instant.now(), null)).isNull();
    }

    @Test
    @DisplayName("a client cannot ask for the whole database")
    void clampsPageSize() {
        assertThat(Cursors.clampLimit(null, 10, 100)).isEqualTo(10);
        assertThat(Cursors.clampLimit(25, 10, 100)).isEqualTo(25);
        assertThat(Cursors.clampLimit(5_000, 10, 100)).isEqualTo(100);
        assertThatThrownBy(() -> Cursors.clampLimit(0, 10, 100)).isInstanceOf(ApiException.class);
    }
}
