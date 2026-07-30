package bd.sammalani.alumni.common.audit;

/**
 * One field, before and after.
 * <p>
 * Both sides are text, including for numbers and timestamps. That is deliberate:
 * these rows are read by a human years later, possibly after the column has
 * changed type or been dropped altogether, and a value that renders as it did on
 * the day survives that. A jsonb number would not, and nobody is going to do
 * arithmetic on an audit trail.
 *
 * @param from null on an insert, and for a field that was previously unset
 * @param to   null on a delete, and for a field that was cleared
 */
public record AuditChange(String from, String to) {

    public static AuditChange set(String to) {
        return new AuditChange(null, to);
    }

    public static AuditChange cleared(String from) {
        return new AuditChange(from, null);
    }
}
