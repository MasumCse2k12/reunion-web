package bd.sammalani.alumni.domain.registration;

import java.util.UUID;

/**
 * Someone the member is bringing. Stored as JSON on the registration rather than
 * as a table: guests are only ever read with their registration, never queried
 * across, and the shape changes with whatever the committee decides to collect.
 */
public record Guest(
        UUID id,
        String name,
        GuestRelation relation,
        Integer age,
        String ticketTypeCode,
        String tshirtSize) {

    public Guest withId(UUID assigned) {
        return new Guest(assigned, name, relation, age, ticketTypeCode, tshirtSize);
    }
}
