package bd.sammalani.alumni.domain.registration;

import java.util.List;

/**
 * The review queue's read side. Kept as a custom fragment rather than a pair of
 * {@code @Query} methods so that the filter predicates are built exactly once
 * and cannot drift between the page query and its count.
 */
public interface ApplicationQueryRepository {

    /** One page, newest first, keyset-positioned. Ask for one more row than you need. */
    List<Registration> findPage(ApplicationQuery query, int limit);

    /** How many rows match the filters, ignoring the cursor. */
    long count(ApplicationQuery query);
}
