package bd.sammalani.alumni.model;

import java.util.List;

public class Registration {
    public String id;
    public String personId;
    public int batchYear;
    public List<Guest> guests;
    public String tshirtSize;
    public String foodPref;
    public String memberNote;
    public double amountDue;
    public String status;        // DRAFT | SUBMITTED | APPROVED | REJECTED
    public String paymentStatus; // UNPAID | REPORTED | CONFIRMED | REJECTED
    public String submittedAt;
    public Review memberReview;
    public Review paymentReview;
    public Payment payment;

    public boolean isDraft()     { return "DRAFT".equals(status); }
    public boolean isSubmitted() { return "SUBMITTED".equals(status); }
    public boolean isApproved()  { return "APPROVED".equals(status); }
    public boolean isRejected()  { return "REJECTED".equals(status); }
    public boolean isLocked()    { return (isSubmitted() || isApproved()) && !isRejected(); }

    public boolean isPendingReview() { return isSubmitted(); }
    public boolean isPayConfirmed() { return "CONFIRMED".equals(paymentStatus); }
    public boolean isPayReported()  { return "REPORTED".equals(paymentStatus); }

    public double totalAmount() {
        return amountDue;
    }

    public int guestCount() {
        return guests != null ? guests.size() : 0;
    }

    public static class Review {
        public String adminName;
        public String at;
        public String note;
    }

    public static class Payment {
        public String method;
        public String reference;
        public double amount;
        public String reportedAt;
    }
}
