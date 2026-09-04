package bd.sammalani.alumni.domain.person;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.security.CurrentUser;
import bd.sammalani.alumni.storage.StorageService;
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
    private final StorageService storage;
    private final AccountDeletionService deletion;

    @GetMapping
    @Operation(summary = "Who am I")
    public PersonDto me() {
        return PersonDto.from(currentPerson());
    }

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
        if (input.batchYear() != null) {
            person.setBatchYear(input.batchYear());
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

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace my profile photo")
    public PersonDto uploadPhoto(@RequestParam("file") MultipartFile file) {
        Person person = currentPerson();
        // Fixed object key — MinIO overwrites the previous photo automatically.
        // No explicit delete needed; the old file is gone the moment the PUT lands.
        String url = storage.upload(person.getId(), file);
        person.setPhotoUrl(url);
        return PersonDto.from(people.save(person));
    }

    @DeleteMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove my profile photo")
    public void deletePhoto() {
        Person person = currentPerson();
        if (person.getPhotoUrl() != null) {
            storage.delete(person.getPhotoUrl());
            person.setPhotoUrl(null);
            people.save(person);
        }
    }

    @GetMapping("/deletion-preview")
    @Operation(summary = "What deleting my account would cost me",
            description = "Read this before showing the confirmation, so the member is told about "
                    + "a paid ticket and given a coordinator to call about it while they still have "
                    + "the app open. Deleting removes the phone number, which is the only way anyone "
                    + "had of contacting them.")
    public AccountDeletionService.DeletionPreview deletionPreview() {
        return deletion.preview(CurrentUser.member().personId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my account and my data",
            description = "Required by Google Play for any app that lets a user create an account. "
                    + "Clears everything the member supplied, deletes their photo from storage and "
                    + "tombstones the record. Confirmed payments survive, flagged for refund — see "
                    + "AccountDeletionService for what is kept and why.")
    public void deleteAccount() {
        deletion.delete(CurrentUser.member().personId());
    }

    private Person currentPerson() {
        return people.findById(CurrentUser.member().personId()).orElseThrow(() -> ApiException.notFound(
                "Profile not found.", "প্রোফাইল পাওয়া যায়নি।"));
    }

    public record ProfileInput(
            @Size(max = 120) String name,
            @Size(max = 120) String nameBn,
            Integer batchYear,
            @Email String email,
            Gender gender,
            LocalDate dob,
            @Size(max = 4) String bloodGroup,
            @Size(max = 120) String occupation,
            @Size(max = 120) String city) {
    }
}
