package bd.sammalani.alumni.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.common.util.PhoneNumbers;
import bd.sammalani.alumni.domain.admin.AdminCredential;
import bd.sammalani.alumni.domain.admin.AdminRepository;
import bd.sammalani.alumni.domain.admin.AdminRole;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.person.PersonStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the first super admin, and only ever on a database that has none.
 * <p>
 * This exists so that no credential is ever written into a Flyway migration,
 * where it would live in git forever and be the same on every deployment. The
 * password comes from the environment and is used once; if it is absent, the
 * service starts anyway and says loudly that nobody can sign in yet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapRunner implements ApplicationRunner {

    private final AdminRepository admins;
    private final PersonRepository people;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap config = props.bootstrap();
        if (!config.enabled() || admins.count() > 0) {
            return;
        }
        if (config.password() == null || config.password().isBlank()) {
            log.warn("No admin accounts exist and app.bootstrap.password is not set. "
                    + "Set it and restart to create the first super admin.");
            return;
        }

        Person person = new Person();
        person.setName(config.name());
        person.setStatus(PersonStatus.VERIFIED);
        if (config.phone() != null && !config.phone().isBlank()) {
            person.setPhone(PhoneNumbers.normalize(config.phone()));
        }
        people.save(person);

        AdminCredential credential = new AdminCredential();
        credential.setPerson(person);
        credential.setUsername(config.username());
        credential.setPasswordHash(passwordEncoder.encode(config.password()));
        credential.setRole(AdminRole.SUPER_ADMIN);
        credential.setActive(true);
        credential.setMustChange(true);
        admins.save(credential);

        log.warn("Created the first super admin '{}' from configuration. "
                + "Change this password now, and remove app.bootstrap.password from the environment.",
                config.username());
    }
}
