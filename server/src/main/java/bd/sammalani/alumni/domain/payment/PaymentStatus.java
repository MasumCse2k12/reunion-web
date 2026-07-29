package bd.sammalani.alumni.domain.payment;

/**
 * Also used on the registration as the queue's filter. UNPAID is not a payment
 * row state — it is the absence of one, and only ever appears on a registration.
 */
public enum PaymentStatus {
    UNPAID,
    REPORTED,
    CONFIRMED,
    REJECTED
}
