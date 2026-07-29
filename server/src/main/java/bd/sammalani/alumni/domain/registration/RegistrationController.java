package bd.sammalani.alumni.domain.registration;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.domain.event.TicketType;
import bd.sammalani.alumni.domain.payment.PaymentRepository;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.CoordinatorDto;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.PaymentReportInput;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.RegistrationDto;
import bd.sammalani.alumni.domain.registration.RegistrationDtos.RegistrationInput;
import bd.sammalani.alumni.domain.review.ReviewRepository;
import bd.sammalani.alumni.domain.review.ReviewSubjectType;
import bd.sammalani.alumni.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me/registration")
@Tag(name = "Registration", description = "The member's own submission for the reunion")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService service;
    private final bd.sammalani.alumni.domain.event.EventService events;
    private final PaymentRepository payments;
    private final ReviewRepository reviews;
    private final PersonRepository people;

    @GetMapping
    @Operation(summary = "My registration, with its decisions and reported payment")
    public RegistrationDto mine() {
        UUID personId = CurrentUser.member().personId();
        Registration registration = service.findMine(personId).orElseThrow(() -> ApiException.notFound(
                "You have not registered yet.", "আপনি এখনো নিবন্ধন করেননি।"));
        return withContext(registration);
    }

    @PutMapping
    @Operation(summary = "Save or replace my draft")
    public RegistrationDto save(@Valid @RequestBody RegistrationInput input) {
        return withContext(service.saveDraft(CurrentUser.member().personId(), input));
    }

    @PostMapping("/submit")
    @Operation(summary = "Send it to my batch coordinator for approval")
    public RegistrationDto submit() {
        return withContext(service.submit(CurrentUser.member().personId()));
    }

    @PostMapping("/payment-report")
    @Operation(summary = "Tell the coordinator what I paid",
            description = "Records a claim. It does not assert that any money arrived — only a coordinator's confirmation does that.")
    public RegistrationDto reportPayment(@Valid @RequestBody PaymentReportInput input) {
        service.reportPayment(CurrentUser.member().personId(), input);
        return mine();
    }

    @GetMapping("/coordinators")
    @Operation(summary = "Who to pay for my batch — name and number, nothing else")
    public List<CoordinatorDto> coordinators() {
        UUID personId = CurrentUser.member().personId();
        Integer batchYear = people.findById(personId)
                .map(bd.sammalani.alumni.domain.person.Person::getBatchYear)
                .orElse(null);
        return batchYear == null ? List.of() : service.coordinatorsFor(batchYear);
    }

    /** Assembles the payment and the two decisions that belong with a registration. */
    private RegistrationDto withContext(Registration registration) {
        var payment = payments.findFirstByRegistrationIdOrderByReportedAtDesc(registration.getId()).orElse(null);
        var memberReview = reviews
                .findLatestForSubjects(ReviewSubjectType.REGISTRATION, List.of(registration.getId()))
                .stream().findFirst().orElse(null);
        var paymentReview = payment == null ? null
                : reviews.findLatestForSubjects(ReviewSubjectType.PAYMENT, List.of(payment.getId()))
                .stream().findFirst().orElse(null);

        Map<String, TicketType> tickets = events.current().getTicketTypes().stream()
                .collect(java.util.stream.Collectors.toMap(TicketType::getCode, Function.identity()));

        return RegistrationMapper.toDto(registration, payment, memberReview, paymentReview, tickets);
    }
}
