package bd.sammalani.alumni.auth;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.auth.AuthDtos.ChallengeResponse;
import bd.sammalani.alumni.auth.AuthDtos.SessionResponse;
import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.common.util.PhoneNumbers;
import bd.sammalani.alumni.config.AppProperties;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonDto;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.person.PersonStatus;
import bd.sammalani.alumni.security.AuthPrincipal;
import bd.sammalani.alumni.security.JwtService;
import bd.sammalani.alumni.security.TokenKind;
import lombok.RequiredArgsConstructor;

/**
 * Members sign in by proving they hold a mobile number. There is no password
 * anywhere in this flow, on purpose: the youngest alum is in their teens and the
 * oldest passed SSC in 1968, and a password is a thing the second group will
 * lose and then stop coming back.
 * <p>
 * Login, claiming a seeded name, and registering a name that is not in the
 * register all converge on the same challenge, so there is one code path to get
 * right rather than three.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PersonRepository people;
    private final OtpService otp;
    private final JwtService jwt;
    private final AppProperties props;

    /** Log in an existing member. */
    @Transactional(readOnly = true)
    public ChallengeResponse requestLoginCode(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        Person person = people.findByPhone(phone).orElseThrow(() -> ApiException.notFound(
                "We do not have that number yet. Please find your name first.",
                "এই নম্বরটি আমাদের কাছে নেই। আগে আপনার নাম খুঁজে নিন।"));
        return toResponse(otp.issue(person.getId(), phone));
    }

    /** Claim a name that is already in the school register. */
    @Transactional
    public ChallengeResponse requestClaimCode(UUID personId, String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        Person person = people.findById(personId).orElseThrow(() -> ApiException.notFound(
                "That name is no longer in the list.", "নামটি আর তালিকায় নেই।"));

        // The number must not already belong to someone else — one verified
        // mobile is one person, and the unique index will say so anyway.
        people.findByPhone(phone).ifPresent(existing -> {
            if (!existing.getId().equals(personId)) {
                throw ApiException.conflict("phone_taken",
                        "That number is already registered to someone else.",
                        "এই নম্বরটি অন্য একজনের নামে নিবন্ধিত।");
            }
        });

        return toResponse(otp.issue(person.getId(), phone));
    }

    /** "My name is not in the list" — create the person, then prove the number. */
    @Transactional
    public ChallengeResponse registerNewName(String name, String nameBn, int batchYear, String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        if (batchYear < props.event().firstBatch() || batchYear > props.event().lastBatch()) {
            throw ApiException.badRequest("unknown_batch",
                    "The school has batches from %d to %d.".formatted(props.event().firstBatch(), props.event().lastBatch()),
                    "বিদ্যালয়ের ব্যাচ %d থেকে %d সাল পর্যন্ত।".formatted(props.event().firstBatch(), props.event().lastBatch()));
        }
        if (people.existsByPhone(phone)) {
            throw ApiException.conflict("phone_taken",
                    "That number is already registered. Try logging in instead.",
                    "এই নম্বরটি আগে থেকেই নিবন্ধিত। লগইন করে দেখুন।");
        }

        // Reuse an existing unverified row (same name + batch, no phone) rather
        // than creating a new one on every abandoned attempt. Without this, the
        // same person submitting the form five times before verifying would
        // produce five SEEDED rows — all visible in batch search.
        Person person = people.findUnverifiedByNameAndBatch(name.strip(), batchYear)
                .orElseGet(Person::new);
        person.setName(name.strip());
        person.setNameBn(nameBn == null || nameBn.isBlank() ? null : nameBn.strip());
        person.setBatchYear(batchYear);
        person.setStatus(PersonStatus.SEEDED);
        people.save(person);

        return toResponse(otp.issue(person.getId(), phone));
    }

    /** Exchange a verified code for a session, binding the number to the person. */
    @Transactional
    public SessionResponse verify(String challengeId, String code) {
        OtpService.Verified verified = otp.verify(challengeId, code);
        Person person = people.findById(verified.personId()).orElseThrow(() -> ApiException.notFound(
                "That profile no longer exists.", "প্রোফাইলটি আর নেই।"));

        person.setPhone(verified.phone());
        if (person.getStatus() == PersonStatus.SEEDED) {
            person.setStatus(PersonStatus.CLAIMED);
        }
        if (person.getClaimedAt() == null) {
            // Stamped on the first proof and never touched again — a later login
            // is not a new claim, and the identity queue pages on this.
            person.setClaimedAt(java.time.Instant.now());
        }
        people.save(person);

        return session(person);
    }

    @Transactional(readOnly = true)
    public SessionResponse refresh(String refreshToken) {
        AuthPrincipal principal = jwt.verify(refreshToken, TokenKind.MEMBER_REFRESH);
        Person person = people.findById(principal.personId()).orElseThrow(() -> ApiException.unauthorized(
                "Your session has ended. Please sign in again.", "আপনার সেশন শেষ হয়েছে। আবার লগইন করুন।"));
        return session(person);
    }

    private SessionResponse session(Person person) {
        String display = person.displayName();
        return new SessionResponse(
                jwt.issueMemberAccess(person.getId(), display),
                jwt.issueMemberRefresh(person.getId(), display),
                jwt.memberAccessTtl().toSeconds(),
                PersonDto.from(person));
    }

    private ChallengeResponse toResponse(OtpService.Challenge challenge) {
        return new ChallengeResponse(challenge.challengeId(), challenge.expiresIn().toSeconds(), challenge.devCode());
    }
}
