package bd.sammalani.alumni.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI alumniOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sammalani Alumni API")
                        .version("v1")
                        .description("""
                                Alumni platform for Sammalani Secondary School, Chalitatala, Narail \
                                (established 1968) and the Grand Reunion 2027.

                                Two audiences share this API and do not share tokens. Members \
                                authenticate with a code sent to their mobile; admins authenticate \
                                with a username and password and receive a token of a different \
                                audience. A member token is never accepted on an /admin route.

                                There is no payment gateway by design: members pay their batch \
                                coordinator offline and report the reference, and a coordinator \
                                confirms it by hand.""")
                        .contact(new Contact().name("Sammalani Alumni Committee"))
                        .license(new License().name("Private")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Member or admin access token, depending on the route.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
