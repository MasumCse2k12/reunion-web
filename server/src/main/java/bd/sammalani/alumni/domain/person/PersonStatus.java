package bd.sammalani.alumni.domain.person;

/**
 * Where a person sits in the identity lifecycle — deliberately separate from a
 * registration's status. Someone can be a VERIFIED alum who is not coming to the
 * reunion, and the platform is meant to outlive the event.
 */
public enum PersonStatus {
    /** Typed in from the school register. Nobody has claimed it. */
    SEEDED,
    /** Someone has proved they hold the phone number on this row. */
    CLAIMED,
    /** A coordinator has confirmed they really are of that batch. */
    VERIFIED,
    /** A coordinator says this claim is not genuine. */
    REJECTED
}
