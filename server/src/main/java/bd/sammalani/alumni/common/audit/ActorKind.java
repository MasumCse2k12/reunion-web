package bd.sammalani.alumni.common.audit;

/**
 * Which kind of caller a write is attributable to.
 * <p>
 * {@code ANONYMOUS} and {@code SYSTEM} are kept apart deliberately. Both mean
 * "no signed-in person", but they are the opposite of each other in an
 * investigation: SYSTEM is startup work nobody could have triggered, while
 * ANONYMOUS is a stranger on the internet — which is exactly who creates a
 * {@code person} row through "my name is not in the list". Collapsing the second
 * into the first would make the most interesting writes in the log look like
 * housekeeping.
 */
public enum ActorKind {

    ADMIN,
    MEMBER,
    /** An unauthenticated caller: the claim and self-registration flows. */
    ANONYMOUS,
    /** Startup and bootstrap. No human behind it, and never a stand-in for one. */
    SYSTEM
}
