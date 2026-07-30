package bd.sammalani.alumni.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import bd.sammalani.alumni.common.audit.AuditActor;
import bd.sammalani.alumni.common.audit.AuditContext;
import bd.sammalani.alumni.common.util.PhoneNumbers;
import bd.sammalani.alumni.domain.admin.AdminCredential;
import bd.sammalani.alumni.domain.admin.AdminRepository;
import bd.sammalani.alumni.domain.admin.AdminRole;
import bd.sammalani.alumni.domain.person.Person;
import bd.sammalani.alumni.domain.person.PersonRepository;
import bd.sammalani.alumni.domain.person.PersonStatus;
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
@Slf4j
public class BootstrapRunner implements ApplicationRunner {

    private final AdminRepository admins;
    private final PersonRepository people;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final TransactionTemplate transactions;

    BootstrapRunner(AdminRepository admins, PersonRepository people, PasswordEncoder passwordEncoder,
                    AppProperties props, PlatformTransactionManager transactionManager) {
        this.admins = admins;
        this.people = people;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * The audit actor is bound here rather than left unattributed, because the
     * very first super admin is the one row in the database that nobody can be
     * asked about afterwards. "system:bootstrap" is the honest answer.
     * <p>
     * The transaction is opened <em>inside</em> that binding, with a template
     * rather than {@code @Transactional}, and the order is the whole reason this
     * method looks like it does. Hibernate defers the inserts to the commit-time
     * flush, which is where the audit listener fires; with {@code @Transactional}
     * on this method the commit would happen as the proxy unwound — after the
     * scoped value had gone out of scope — and the first two rows in the database
     * would be the only unattributed ones in it.
     */
    @Override
    public void run(ApplicationArguments args) {
        AuditContext.runAs(AuditActor.system("bootstrap"),
                () -> transactions.executeWithoutResult(status -> createFirstAdmin()));
    }

    private void createFirstAdmin() {
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
