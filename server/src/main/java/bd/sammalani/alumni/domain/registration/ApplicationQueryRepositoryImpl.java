package bd.sammalani.alumni.domain.registration;

import java.util.ArrayList;
import java.util.List;

import bd.sammalani.alumni.domain.payment.Payment;
import bd.sammalani.alumni.domain.person.Person;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Criteria implementation of the queue read.
 * <p>
 * Two things matter for performance here. The ordering is always
 * {@code (submitted_at desc, id desc)} and the cursor compares against the same
 * pair, so every page is an index scan over one of the four registration queue
 * indexes rather than an OFFSET that walks rows it will throw away. And the page
 * fetches the person along with the registration, because the caller renders a
 * name for every row and would otherwise issue a query per row.
 */
class ApplicationQueryRepositoryImpl implements ApplicationQueryRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Registration> findPage(ApplicationQuery query, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Registration> cq = cb.createQuery(Registration.class);
        Root<Registration> root = cq.from(Registration.class);
        root.fetch("person", JoinType.INNER);

        cq.where(predicates(cb, cq, root, query, true));
        cq.orderBy(cb.desc(root.get("submittedAt")), cb.desc(root.get("id")));

        return em.createQuery(cq)
                .setMaxResults(limit)
                // A queue read never needs dirty checking on what it returns.
                .setHint("org.hibernate.readOnly", true)
                .getResultList();
    }

    @Override
    public long count(ApplicationQuery query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Registration> root = cq.from(Registration.class);

        cq.select(cb.count(root))
                .where(predicates(cb, cq, root, query, false));

        return em.createQuery(cq).getSingleResult();
    }

    /**
     * @param withCursor false for the count, which describes the whole result
     *                   set rather than the page the caller has reached
     */
    private Predicate[] predicates(CriteriaBuilder cb, CriteriaQuery<?> cq, Root<Registration> root,
                                   ApplicationQuery q, boolean withCursor) {
        List<Predicate> where = new ArrayList<>();

        // A draft is not an application: nobody has asked for it to be judged.
        where.add(cb.notEqual(root.get("status"), RegistrationStatus.DRAFT));

        if (q.memberStatus() != null) {
            where.add(cb.equal(root.get("status"), q.memberStatus()));
        }
        if (q.paymentStatus() != null) {
            where.add(cb.equal(root.get("paymentStatus"), q.paymentStatus()));
        }
        if (q.batchYear() != null) {
            where.add(cb.equal(root.get("batchYear"), q.batchYear()));
        }

        // Authority, resolved from the session. A group admin with no batches
        // assigned sees nothing — never everything.
        if (!q.allBatches()) {
            where.add(q.scope().isEmpty()
                    ? cb.disjunction()
                    : root.get("batchYear").in(q.scope()));
        }

        if (q.search() != null && !q.search().isBlank()) {
            where.add(searchPredicate(cb, cq, root, q.search().strip().toLowerCase()));
        }

        if (withCursor && q.cursorSubmittedAt() != null && q.cursorId() != null) {
            Expression<java.time.Instant> submittedAt = root.get("submittedAt");
            Predicate older = cb.lessThan(submittedAt, q.cursorSubmittedAt());
            Predicate sameInstantLowerId = cb.and(
                    cb.equal(submittedAt, q.cursorSubmittedAt()),
                    cb.lessThan(root.get("id"), q.cursorId()));
            where.add(cb.or(older, sameInstantLowerId));
        }

        return where.toArray(Predicate[]::new);
    }

    /**
     * Name in either script, mobile, batch year, or the transaction reference the
     * member typed in — a coordinator holding a bKash statement searches by TrxID.
     */
    private Predicate searchPredicate(CriteriaBuilder cb, CriteriaQuery<?> cq, Root<Registration> root, String term) {
        String like = "%" + term + "%";
        var person = root.<Registration, Person>join("person");

        Subquery<Long> byReference = cq.subquery(Long.class);
        Root<Payment> payment = byReference.from(Payment.class);
        byReference.select(cb.literal(1L))
                .where(cb.equal(payment.get("registration"), root),
                        cb.like(cb.lower(cb.coalesce(payment.get("reference"), "")), like));

        return cb.or(
                cb.like(cb.lower(person.get("name")), like),
                cb.like(cb.lower(cb.coalesce(person.get("nameBn"), "")), like),
                cb.like(cb.coalesce(person.get("phone"), ""), like),
                cb.like(cb.lower(cb.coalesce(person.get("email"), "")), like),
                cb.like(root.get("batchYear").as(String.class), like),
                cb.exists(byReference));
    }
}
