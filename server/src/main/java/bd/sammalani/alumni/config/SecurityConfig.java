package bd.sammalani.alumni.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import bd.sammalani.alumni.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_GET = {
            // Coverage counters only. Note the single wildcard: /batches/{year}/members
            // is a list of real people and deliberately does not match.
            "/api/v1/batches",
            "/api/v1/batches/totals",
            "/api/v1/events/**",
            "/api/v1/notices",
            "/api/v1/public/**",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppProperties props;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // No cookies, no sessions, so nothing for a forged form post to ride on.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        // Getting a code and exchanging it are necessarily open.
                        .requestMatchers("/api/v1/auth/**", "/api/v1/public/**").permitAll()
                        .requestMatchers("/api/v1/admin/auth/login").permitAll()
                        // Everything else behind /admin needs an admin token — the
                        // audience check in the filter means a member token is not one.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().hasRole("MEMBER"))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.getWriter().write("""
                                    {"status":401,"code":"unauthorized",\
                                    "detail":"Please log in to continue.",\
                                    "messageBn":"চালিয়ে যেতে লগইন করুন।"}""");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.getWriter().write("""
                                    {"status":403,"code":"forbidden",\
                                    "detail":"You are not allowed to do that.",\
                                    "messageBn":"আপনার এই কাজের অনুমতি নেই।"}""");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Argon2id, at Spring Security's current defaults. Deliberately not BCrypt:
     * these hashes will sit in a database that outlives the reunion by decades.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
