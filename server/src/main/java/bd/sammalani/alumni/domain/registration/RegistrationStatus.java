package bd.sammalani.alumni.domain.registration;

/**
 * Where a submission sits. Distinct from {@code PersonStatus}: this is about one
 * event, that is about who someone is.
 */
public enum RegistrationStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED
}
