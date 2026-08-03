package bd.sammalani.alumni.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import bd.sammalani.alumni.admin.AdminDtos.BulkDecisionResponse;
import bd.sammalani.alumni.admin.AdminDtos.MemberStatus;
import bd.sammalani.alumni.admin.AdminDtos.MemberVerdict;
import bd.sammalani.alumni.admin.AdminDtos.PaymentVerdict;
import bd.sammalani.alumni.admin.AdminDtos.QueueKind;
import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.domain.admin.AdminRole;
import bd.sammalani.alumni.domain.batch.BatchRepository;
import bd.sammalani.alumni.domain.payment.Payment;
import bd.sammalani.alumni.domain.payment.PaymentRepository;
import bd.sammalani.alumni.domain.payment.PaymentStatus;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.person.PersonStatus;
import bd.sammalani.alumni.domain.registration.ApplicationQuery;
import bd.sammalani.alumni.domain.registration.Registration;
import bd.sammalani.alumni.domain.registration.RegistrationRepository;
import bd.sammalani.alumni.domain.registration.RegistrationStatus;
import bd.sammalani.alumni.domain.review.ReviewRepository;

/**
 * A decision is once. These cover the guard rather than the writes: what the
 * queue refuses, and to whom.
 */
@ExtendWith(MockitoExtension.class)
class AdminQueueDecisionTest {

    private static final int BATCH = 2010;

    @Mock private RegistrationRepository registrations;
    @Mock private BatchRepository batches;
    @Mock private PaymentRepository payments;
    @Mock private PersonRepository people;
    @Mock private ReviewRepository reviews;
    @Mock private AdminContextService context;
    @Mock private ApplicationAssembler assembler;

    private AdminQueueService queue;

    @BeforeEach
    void setUp() {
        queue = new AdminQueueService(registrations, batches, payments, people, reviews, context, assembler);
        lenient().when(assembler.assembleOne(any())).thenReturn(null);
        lenient().when(assembler.assemble(any())).thenReturn(List.of());
    }

    /* ---------------- members ---------------- */

    @Test
    @DisplayName("a submitted registration is decided normally")
    void submittedIsDecidable() {
        Registration r = registration(RegistrationStatus.SUBMITTED, PaymentStatus.UNPAID);
        given(groupAdmin(), r);

        queue.decideMember(r.getId(), MemberVerdict.APPROVED, null);

        assertThat(r.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(r.getQrToken()).isNotNull();
        assertThat(r.getPerson().getStatus()).isEqualTo(PersonStatus.VERIFIED);
    }

    @Test
    @DisplayName("an approved member cannot be approved again, by anyone")
    void noRepeatApproval() {
        Registration r = registration(RegistrationStatus.APPROVED, PaymentStatus.UNPAID);
        given(superAdmin(), r);

        assertThatThrownBy(() -> queue.decideMember(r.getId(), MemberVerdict.APPROVED, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already approved");
        verify(reviews, never()).save(any());
    }

    @Test
    @DisplayName("a group admin cannot overturn a settled member decision")
    void groupAdminCannotOverturn() {
        Registration r = registration(RegistrationStatus.APPROVED, PaymentStatus.UNPAID);
        given(groupAdmin(), r);

        assertThatThrownBy(() -> queue.decideMember(r.getId(), MemberVerdict.REJECTED, "wrong person"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
    }

    @Test
    @DisplayName("a super admin reversing an approval takes the gate pass back with it")
    void reversalWithdrawsTheQrToken() {
        Registration r = registration(RegistrationStatus.APPROVED, PaymentStatus.UNPAID);
        r.setQrToken("issued-on-approval");
        r.getPerson().setStatus(PersonStatus.VERIFIED);
        given(superAdmin(), r);

        queue.decideMember(r.getId(), MemberVerdict.REJECTED, "not of this batch");

        assertThat(r.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
        // The whole point: a rejected member must not still scan through the gate.
        assertThat(r.getQrToken()).isNull();
    }

    @Test
    @DisplayName("rejecting downgrades a claimed person, and leaves a verified one alone")
    void rejectionReachesBackOnlyToUnvouchedPeople() {
        Registration claimed = registration(RegistrationStatus.SUBMITTED, PaymentStatus.UNPAID);
        claimed.getPerson().setStatus(PersonStatus.CLAIMED);
        given(groupAdmin(), claimed);
        queue.decideMember(claimed.getId(), MemberVerdict.REJECTED, "duplicate");
        assertThat(claimed.getPerson().getStatus()).isEqualTo(PersonStatus.REJECTED);

        // Someone a coordinator has already vouched for stays vouched for: a seat
        // refused for this event says nothing about which batch they sat in.
        Registration verified = registration(RegistrationStatus.SUBMITTED, PaymentStatus.UNPAID);
        verified.getPerson().setStatus(PersonStatus.VERIFIED);
        given(groupAdmin(), verified);
        queue.decideMember(verified.getId(), MemberVerdict.REJECTED, "not attending");
        assertThat(verified.getPerson().getStatus()).isEqualTo(PersonStatus.VERIFIED);
    }

    @Test
    @DisplayName("a member outside the caller's batches is refused, not decided")
    void outsideScopeIsForbidden() {
        Registration r = registration(RegistrationStatus.SUBMITTED, PaymentStatus.UNPAID);
        r.setBatchYear(1974);
        given(groupAdmin(), r);

        assertThatThrownBy(() -> queue.decideMember(r.getId(), MemberVerdict.APPROVED, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /* ---------------- payments ---------------- */

    @Test
    @DisplayName("a confirmed payment cannot be confirmed twice")
    void noRepeatConfirmation() {
        Registration r = registration(RegistrationStatus.APPROVED, PaymentStatus.CONFIRMED);
        givenPayment(superAdmin(), r, PaymentStatus.CONFIRMED);

        assertThatThrownBy(() -> queue.decidePayment(r.getId(), PaymentVerdict.CONFIRMED, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already confirmed");
    }

    @Test
    @DisplayName("only a super admin may reverse a confirmed payment")
    void reversingMoneyIsSuperAdminOnly() {
        Registration r = registration(RegistrationStatus.APPROVED, PaymentStatus.CONFIRMED);
        givenPayment(groupAdmin(), r, PaymentStatus.CONFIRMED);

        assertThatThrownBy(() -> queue.decidePayment(r.getId(), PaymentVerdict.REJECTED, "bounced"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("super admin");
        assertThat(r.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("a rejected payment is not settled — the member may still be paid up")
    void rejectedPaymentCanStillBeConfirmed() {
        Registration r = registration(RegistrationStatus.APPROVED, PaymentStatus.REJECTED);
        givenPayment(groupAdmin(), r, PaymentStatus.REJECTED);

        queue.decidePayment(r.getId(), PaymentVerdict.CONFIRMED, "found it on the statement");

        assertThat(r.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("money cannot be confirmed for a seat nobody has granted")
    void confirmingNeedsAnApprovedMember() {
        Registration r = registration(RegistrationStatus.SUBMITTED, PaymentStatus.REPORTED);
        givenPayment(groupAdmin(), r, PaymentStatus.REPORTED);

        assertThatThrownBy(() -> queue.decidePayment(r.getId(), PaymentVerdict.CONFIRMED, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Approve this member");
        assertThat(r.getPaymentStatus()).isEqualTo(PaymentStatus.REPORTED);
    }

    @Test
    @DisplayName("a wrong claim against an unapproved registration can still be cleared")
    void rejectingDoesNotNeedApproval() {
        Registration r = registration(RegistrationStatus.SUBMITTED, PaymentStatus.REPORTED);
        givenPayment(groupAdmin(), r, PaymentStatus.REPORTED);

        queue.decidePayment(r.getId(), PaymentVerdict.REJECTED, "wrong transaction number");

        assertThat(r.getPaymentStatus()).isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("the payments queue is approved members only, whatever the caller asks for")
    void paymentsQueueOverridesTheMemberFilter() {
        when(context.current()).thenReturn(groupAdmin());
        when(registrations.findPage(any(), anyInt())).thenReturn(List.of());
        when(registrations.count(any(ApplicationQuery.class))).thenReturn(0L);

        // A caller asking the money queue for pending members gets approved ones.
        queue.page(QueueKind.PAYMENTS, MemberStatus.PENDING, PaymentStatus.REPORTED, null, null, null, null);

        ArgumentCaptor<ApplicationQuery> sent = ArgumentCaptor.forClass(ApplicationQuery.class);
        verify(registrations).findPage(sent.capture(), anyInt());
        assertThat(sent.getValue().memberStatus()).isEqualTo(RegistrationStatus.APPROVED);
    }

    @Test
    @DisplayName("there is nothing to reject when nothing was ever reported")
    void rejectingNothingIsRefused() {
        Registration r = registration(RegistrationStatus.APPROVED, PaymentStatus.UNPAID);
        given(groupAdmin(), r);
        when(payments.findFirstByRegistrationIdOrderByReportedAtDesc(r.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queue.decidePayment(r.getId(), PaymentVerdict.REJECTED, "no"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no payment to reject");
    }

    /* ---------------- bulk ---------------- */

    @Test
    @DisplayName("a bulk sweep over settled rows decides none of them and says so by name")
    void bulkSkipsSettledRows() {
        Registration settled = registration(RegistrationStatus.APPROVED, PaymentStatus.UNPAID);
        Registration open = registration(RegistrationStatus.SUBMITTED, PaymentStatus.UNPAID);
        when(context.current()).thenReturn(groupAdmin());
        when(registrations.findWithDetailsById(settled.getId())).thenReturn(Optional.of(settled));
        when(registrations.findWithDetailsById(open.getId())).thenReturn(Optional.of(open));

        BulkDecisionResponse response =
                queue.decideMembers(List.of(settled.getId(), open.getId()), MemberVerdict.APPROVED, null);

        assertThat(response.skipped()).singleElement()
                .satisfies(s -> assertThat(s.reason()).contains("already approved"));
        assertThat(open.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
    }

    /* ---------------- fixtures ---------------- */

    private void given(AdminSession admin, Registration r) {
        when(context.current()).thenReturn(admin);
        when(registrations.findWithDetailsById(r.getId())).thenReturn(Optional.of(r));
    }

    private void givenPayment(AdminSession admin, Registration r, PaymentStatus status) {
        given(admin, r);
        Payment payment = new Payment();
        payment.setRegistration(r);
        payment.setStatus(status);
        when(payments.findFirstByRegistrationIdOrderByReportedAtDesc(r.getId())).thenReturn(Optional.of(payment));
    }

    private static Registration registration(RegistrationStatus status, PaymentStatus paymentStatus) {
        Person person = new Person();
        person.setName("Rahim Uddin");
        person.setBatchYear(BATCH);
        person.setStatus(PersonStatus.CLAIMED);

        Registration r = new Registration();
        r.setId(UUID.randomUUID());
        r.setPerson(person);
        r.setBatchYear(BATCH);
        r.setStatus(status);
        r.setPaymentStatus(paymentStatus);
        return r;
    }

    private static AdminSession groupAdmin() {
        return new AdminSession(UUID.randomUUID(), "Coordinator", null, "coordinator",
                AdminRole.GROUP_ADMIN, Set.of(BATCH), true, false);
    }

    private static AdminSession superAdmin() {
        return new AdminSession(UUID.randomUUID(), "Chief", null, "chief",
                AdminRole.SUPER_ADMIN, Set.of(), true, false);
    }
}
