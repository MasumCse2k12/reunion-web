package bd.sammalani.alumni.domain.person;

import java.time.LocalDate;
import java.util.UUID;

import bd.sammalani.alumni.common.util.PhoneNumbers;

/**
 * A person as the owner of that record may see themselves.
 * <p>
 * Built by a static factory rather than a mapping framework: a record with a
 * one-line {@code from} is less machinery than a generated mapper, and the
 * compiler catches a missing field either way.
 */
public record PersonDto(
        UUID id,
        String name,
        String nameBn,
        Integer batchYear,
        PersonStatus status,
        String phone,
        String email,
        Gender gender,
        LocalDate dob,
        String bloodGroup,
        String occupation,
        String city,
        boolean deceased) {

    public static PersonDto from(Person p) {
        return new PersonDto(p.getId(), p.getName(), p.getNameBn(), p.getBatchYear(), p.getStatus(),
                p.getPhone(), p.getEmail(), p.getGender(), p.getDob(), p.getBloodGroup(),
                p.getOccupation(), p.getCity(), p.isDeceased());
    }

    /**
     * The same person as seen by someone who is not them: name and batch, with
     * the number masked. The single most important privacy control in the
     * product — a phone number is never public by default.
     */
    public static PersonDto masked(Person p) {
        return new PersonDto(p.getId(), p.getName(), p.getNameBn(), p.getBatchYear(), p.getStatus(),
                PhoneNumbers.mask(p.getPhone()), null, null, null, null,
                p.getOccupation(), p.getCity(), p.isDeceased());
    }
}
