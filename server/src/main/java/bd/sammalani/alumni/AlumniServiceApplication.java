package bd.sammalani.alumni;

import bd.sammalani.alumni.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableCaching
@EnableJpaAuditing
@EnableTransactionManagement
@EnableConfigurationProperties(AppProperties.class)
public class AlumniServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlumniServiceApplication.class, args);
    }
}
