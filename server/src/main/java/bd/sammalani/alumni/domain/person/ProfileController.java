package bd.sammalani.alumni.domain.person;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Profile", description = "The signed-in member's own record")
@RequiredArgsConstructor
public class ProfileController {

    private final PersonRepository people;

    @GetMapping
    @Operation(summary = "Who am I")
    public PersonDto me() {
        return PersonDto.from(currentPerson());
    }

    /**
     * A patch, not a put: the profile screen saves one section at a time, and a
     * null here means "not supplied" rather than "clear it". Batch year and phone
     * are absent on purpose — changing either is an identity change, and it goes
     * through a coordinator rather than a text field.
     */
    @PatchMapping
    @Operation(summary = "Update my details")
    public PersonDto update(@Valid @RequestBody ProfileInput input) {
        Person person = currentPerson();
        if (input.name() != null && !input.name().isBlank()) {
            person.setName(input.name().strip());
        }
        if (input.nameBn() != null) {
            person.setNameBn(input.nameBn().isBlank() ? null : input.nameBn().strip());
        }
        if (input.email() != null) {
            person.setEmail(input.email().isBlank() ? null : input.email().strip());
        }
        if (input.gender() != null) {
            person.setGender(input.gender());
        }
        if (input.dob() != null) {
            person.setDob(input.dob());
        }
        if (input.bloodGroup() != null) {
            person.setBloodGroup(input.bloodGroup().isBlank() ? null : input.bloodGroup());
        }
        if (input.occupation() != null) {
            person.setOccupation(input.occupation().isBlank() ? null : input.occupation().strip());
        }
        if (input.city() != null) {
            person.setCity(input.city().isBlank() ? null : input.city().strip());
        }
        return PersonDto.from(people.save(person));
    }

    private Person currentPerson() {
        return people.findById(CurrentUser.member().personId()).orElseThrow(() -> ApiException.notFound(
                "Profile not found.", "প্রোফাইল পাওয়া যায়নি।"));
    }

    public record ProfileInput(
            @Size(max = 120) String name,
            @Size(max = 120) String nameBn,
            @Email String email,
            Gender gender,
            LocalDate dob,
            @Size(max = 4) String bloodGroup,
            @Size(max = 120) String occupation,
            @Size(max = 120) String city) {
    }
}
