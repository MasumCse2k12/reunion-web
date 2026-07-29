package bd.sammalani.alumni.domain.referral;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.common.util.PhoneNumbers;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * "I know this person." The single most valuable thing a member can do for the
 * early batches, so it asks for as little as possible: a name and a number.
 */
@RestController
@RequestMapping("/api/v1/referrals")
@Tag(name = "Referrals", description = "Helping the committee find missing classmates")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralRepository referrals;
    private final PersonRepository people;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Share a lead on someone who has not been found")
    public void submit(@Valid @RequestBody ReferralInput input) {
        String phone = input.phone() == null || input.phone().isBlank()
                ? null
                : PhoneNumbers.normalize(input.phone());

        // Duplicate leads are common and harmless — the same classmate gets
        // reported by four people. Silently accept rather than tell the member
        // off for helping twice.
        if (phone != null && referrals.existsByPhoneAndBatchYear(phone, input.batchYear())) {
            return;
        }

        Referral referral = new Referral();
        referral.setName(input.name().strip());
        referral.setPhone(phone);
        referral.setBatchYear(input.batchYear());
        referral.setNote(input.note());
        people.findById(CurrentUser.member().personId()).ifPresent(referral::setReferrer);
        referrals.save(referral);
    }

    public record ReferralInput(
            @NotBlank @Size(max = 120) String name,
            String phone,
            Integer batchYear,
            @Size(max = 500) String note) {
    }
}
