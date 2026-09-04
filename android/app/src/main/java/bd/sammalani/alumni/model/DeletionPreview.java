package bd.sammalani.alumni.model;

import java.util.List;

/**
 * What the member stands to lose by deleting their account, read before the
 * confirmation is shown rather than after it is acted on.
 * <p>
 * {@link #coordinators} matters more than it looks: deleting the account clears
 * the mobile number, which is the only way the committee had of reaching this
 * member. So the number they would need to call about a refund has to be put in
 * front of them while they can still read it.
 */
public class DeletionPreview {
    public boolean hasRegistration;
    public String registrationStatus;   // DRAFT | SUBMITTED | APPROVED | REJECTED | CANCELLED
    public double amountPaid;           // confirmed money only
    public boolean refundPending;
    public List<Coordinator> coordinators;

    public boolean hasPaid() { return refundPending && amountPaid > 0; }
}
