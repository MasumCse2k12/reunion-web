package bd.sammalani.alumni.domain.registration;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import bd.sammalani.alumni.domain.payment.PaymentStatus;

/**
 * Everything that narrows the review queue, in one value.
 * <p>
 * {@code scope} is the caller's authority, not a user-supplied filter: null
 * means a super admin who sees all 59 batches, and any other value is the exact
 * set of years a group admin may see. It is resolved from the session, never
 * from the request, which is why it sits alongside the filters rather than in
 * them.
 *
 * @param cursorSubmittedAt keyset position — the last row of the previous page
 */
public record ApplicationQuery(
        RegistrationStatus memberStatus,
        PaymentStatus paymentStatus,
        Integer batchYear,
        String search,
        Set<Integer> scope,
        Instant cursorSubmittedAt,
        UUID cursorId) {

    public boolean allBatches() {
        return scope == null;
    }
}
