package bd.sammalani.alumni.auth;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.auth.AuthDtos.ChallengeResponse;
import bd.sammalani.alumni.auth.AuthDtos.ClaimRequest;
import bd.sammalani.alumni.auth.AuthDtos.SelfRegisterRequest;
import bd.sammalani.alumni.domain.person.PersonDto;
import bd.sammalani.alumni.domain.person.PersonRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The "find your name" flow, which necessarily runs before anyone has a session.
 * Everything here is masked: a stranger can confirm that a name exists on a
 * batch, and learn nothing else about the person holding it.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Claim", description = "Finding your name in the school register")
@SecurityRequirements
@RequiredArgsConstructor
public class PublicController {

    private static final int MAX_CANDIDATES = 50;

    private final PersonRepository people;
    private final AuthService auth;

    @GetMapping("/lookup")
    @Operation(summary = "Search a batch's register by name",
            description = "Returns name, batch and a masked number only.")
    public List<PersonDto> lookup(@RequestParam int batchYear, @RequestParam(required = false) String q) {
        return people.searchInBatch(batchYear, q, Limit.of(MAX_CANDIDATES))
                .stream()
                .map(PersonDto::masked)
                .toList();
    }

    @PostMapping("/claims")
    @Operation(summary = "\"This is me\" — send a code to bind this number to that name")
    public ChallengeResponse claim(@Valid @RequestBody ClaimRequest request) {
        return auth.requestClaimCode(request.personId(), request.phone());
    }

    @PostMapping("/register")
    @Operation(summary = "\"My name is not in the list\" — create it, then prove the number")
    public ChallengeResponse register(@Valid @RequestBody SelfRegisterRequest request) {
        return auth.registerNewName(request.name(), request.nameBn(), request.batchYear(), request.phone());
    }
}
