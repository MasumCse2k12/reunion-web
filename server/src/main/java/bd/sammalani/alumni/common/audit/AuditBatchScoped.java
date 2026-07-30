package bd.sammalani.alumni.common.audit;

/**
 * An entity that can say which batch year a change to it belongs to.
 * <p>
 * The batch year is copied onto every audit row so that a group admin reading the
 * trail sees their own years and no others — the same rule the review queue
 * follows, enforced the same way. The entity supplies it rather than the audit
 * code reading a field by reflection, because for one entity the answer is not a
 * field at all: a payment's year lives on its registration, and fetching it from
 * inside a flush would initialise a proxy mid-write. Only the entity knows that.
 */
public interface AuditBatchScoped {

    /**
     * @return the batch year this row belongs to, or null when it has none — a
     *         row with no year is visible to a super admin only, which is the
     *         safe direction for the default to fail in
     */
    Integer auditBatchYear();
}
