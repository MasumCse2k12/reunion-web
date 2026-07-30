package bd.sammalani.alumni.common.audit;

import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns a row's before and after into the diff an audit row carries.
 * <p>
 * Three rules, and each of them exists because of a way an audit trail goes
 * wrong rather than because of a way it goes right:
 * <ul>
 *   <li><strong>Secrets are never recorded.</strong> A password hash written into
 *       a table that is deliberately never deleted, and that a coordinator can
 *       read through the portal, is a worse leak than the one auditing prevents.
 *       The list is a denylist by field name, checked here rather than at each
 *       call site, so a field added to {@code AdminCredential} next year is
 *       covered by adding one string in one place.</li>
 *   <li><strong>The timestamps are ignored.</strong> {@code updatedAt} changes on
 *       every save, so recording it would turn "this row changed" into noise and
 *       leave a trail of rows that say nothing happened.</li>
 *   <li><strong>Values are truncated.</strong> {@code person.extras} is an open
 *       jsonb bag and {@code member_note} is free text; a diff of two 8 KB
 *       values, twice per row, is how a log table becomes the largest thing in
 *       the backup.</li>
 * </ul>
 */
public final class AuditChanges {

    /**
     * Never recorded, in any action, from or to. Names are entity property names,
     * not columns.
     */
    static final Set<String> SECRET = Set.of(
            "passwordHash",
            // The e-ticket's bearer token: whoever holds it can walk in as someone else.
            "qrToken");

    /** Bookkeeping that changes on every write and explains nothing. */
    static final Set<String> BOOKKEEPING = Set.of("createdAt", "updatedAt");

    static final String SECRET_MARKER = "«set»";
    static final String SECRET_CLEARED = "«unset»";

    /**
     * Long enough for a member's note or a name in either script, short enough
     * that no single row can be a problem.
     */
    static final int MAX_LENGTH = 300;

    private AuditChanges() {
    }

    /**
     * Everything a new row was created with. Nulls are left out — on an insert
     * they are the absence of information, and listing forty of them buries the
     * eight fields somebody actually filled in.
     */
    public static Map<String, AuditChange> ofInsert(String[] names, String[] values) {
        Map<String, AuditChange> changes = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            if (skip(names[i]) || values[i] == null) {
                continue;
            }
            changes.put(names[i], AuditChange.set(mask(names[i], values[i])));
        }
        return changes;
    }

    /**
     * The last state of a row before it was tombstoned. Recorded in full, because
     * this is the snapshot somebody will want when they ask to have a removal
     * reversed and the row itself no longer answers questions.
     */
    public static Map<String, AuditChange> ofDelete(String[] names, String[] values) {
        Map<String, AuditChange> changes = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            if (skip(names[i]) || values[i] == null) {
                continue;
            }
            changes.put(names[i], AuditChange.cleared(mask(names[i], values[i])));
        }
        return changes;
    }

    /**
     * What actually changed.
     *
     * @param dirty the indexes Hibernate found dirty, or null when it could not
     *              tell — in which case every field is compared, because guessing
     *              "nothing changed" would drop the row from the trail
     * @return an empty map when the only difference was bookkeeping, which is the
     *         signal to write no audit row at all
     */
    public static Map<String, AuditChange> ofUpdate(String[] names, String[] before, String[] after, int[] dirty) {
        Map<String, AuditChange> changes = new LinkedHashMap<>();
        for (int i : dirty == null ? allIndexes(names.length) : dirty) {
            if (i < 0 || i >= names.length || skip(names[i])) {
                continue;
            }
            String from = before == null ? null : before[i];
            String to = after[i];
            if (java.util.Objects.equals(from, to)) {
                continue;
            }
            changes.put(names[i], new AuditChange(mask(names[i], from), mask(names[i], to)));
        }
        return changes;
    }

    /**
     * A value as the trail should hold it: a short, stable string, and never
     * something whose {@code toString} might go back to the database. Entity
     * references are resolved to their identifier by the caller, which is the one
     * place that can do it without initialising a proxy.
     */
    public static String render(Object value) {
        if (value == null) {
            return null;
        }
        String text = switch (value) {
            case CharSequence s -> s.toString();
            case Enum<?> e -> e.name();
            case Temporal t -> t.toString();
            case byte[] bytes -> "«%d bytes»".formatted(bytes.length);
            // A jsonb bag or an embedded list. toString is safe here only because
            // the caller has already excluded mapped collections, which would be
            // a lazy proxy and a query.
            case Collection<?> c -> c.toString();
            case Map<?, ?> m -> m.toString();
            default -> String.valueOf(value);
        };
        return truncate(text);
    }

    private static boolean skip(String name) {
        return BOOKKEEPING.contains(name);
    }

    /**
     * A secret's presence is worth recording; its value never is. "the password
     * was changed" is the auditable fact, and it is the whole of the fact.
     */
    private static String mask(String name, String value) {
        if (!SECRET.contains(name)) {
            return value;
        }
        return value == null ? SECRET_CLEARED : SECRET_MARKER;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH) + "…";
    }

    private static int[] allIndexes(int length) {
        int[] all = new int[length];
        Arrays.setAll(all, i -> i);
        return all;
    }
}
