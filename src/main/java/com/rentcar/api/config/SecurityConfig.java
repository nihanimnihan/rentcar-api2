package com.rentcar.api.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@ConfigurationPropertiesScan
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Single in-memory admin user. The plain-text password from properties is
     * BCrypt-encoded once at startup — it is never stored hashed in source code.
     */
    @Bean
    public UserDetailsService userDetailsService(AdminProperties props, PasswordEncoder encoder) {
        UserDetails admin = User.builder()
                .username(props.username())
                .password(encoder.encode(props.password()))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OAuth2LoginSuccessHandler successHandler) throws Exception {
        http
                /*
                 * CSRF disabled: keep disabled for now to avoid breaking existing JS fetch()
                 * used by guest checkout. OAuth2 login will use the session cookie but
                 * we rely on same-origin protections and internal-only returnTo handling
                 * to reduce risk. Review CSRF posture before production.
                 */
                .csrf(csrf -> csrf.disable())

                // Allow HttpSession for OAuth2 login flows; other APIs remain usable with or without sessions.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                .authorizeHttpRequests(auth -> auth
                        // ── Static frontend resources ──────────────────────────────────────
                        .requestMatchers("/", "/*.html").permitAll()
                        .requestMatchers("/admin/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/fonts/**", "/partials/**").permitAll()
                        // H2 console (dev profile only — harmless to permit in prod since
                        // h2-console is disabled there via application-prod.yaml)
                        .requestMatchers("/h2-console/**").permitAll()
                        // Actuator health — public so load balancers / uptime monitors can probe
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/config/**").permitAll()

                        // ── Public API — car browsing ───────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/cars/search", "/api/cars/popular").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/cars/{id}").permitAll()

                        // ── Public API — booking and payment ───────────────────────────────
                        // These two must come BEFORE the admin /api/bookings/** rule below.
                        .requestMatchers(HttpMethod.POST, "/api/bookings").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/bookings/*/payments/intent").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/bookings/*/payments/process").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/checkout").permitAll()
                        // Manage booking lookup — public, requires reference + lastName (no auth).
                        // Must come BEFORE the admin GET /api/bookings/** rule below.
                        .requestMatchers(HttpMethod.GET, "/api/bookings/manage").permitAll()
                        // Cancellation policy preview — public, same identity rules as manage lookup.
                        .requestMatchers(HttpMethod.GET, "/api/bookings/manage/cancellation-policy").permitAll()
                        // Customer-facing cancel — authenticated by reference + lastName, no numeric id.
                        .requestMatchers(HttpMethod.POST, "/api/bookings/manage/cancel").permitAll()

                        // ── Public API — add-ons list (must come BEFORE the admin rule below) ─
                        .requestMatchers(HttpMethod.GET, "/api/addons/active").permitAll()

                        // ── Public API — transfer durations + offers ───────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/transfer/durations").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/transfer/offers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/transfer/bookings").permitAll()

                        // ── Admin (DEMO: open for presentation — TODO before production: restore hasRole("ADMIN")) ──
                        .requestMatchers("/api/admin/**").permitAll() // TODO: .hasRole("ADMIN")
                        .requestMatchers("/api/payments/**").hasRole("ADMIN")

                        // Public auth endpoints
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/profile").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()

                        // Booking reads and cancellation are admin-only.
                        // The public POST /api/bookings and POST /api/bookings/*/payments/process
                        // rules above already match before reaching this line.
                        .requestMatchers(HttpMethod.POST, "/api/bookings/*/cancel").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/bookings/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/customers/**").hasRole("ADMIN")
                        .requestMatchers("/api/addons/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/cars/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/cars/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/cars/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/cars/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                // HTTP Basic with a JSON 401 — no Spring redirect to a login page.
                .httpBasic(basic -> basic
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write(
                                    "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                        })
                )

                // JSON 403 for authenticated users without the ADMIN role.
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write(
                                    "{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        })
                )

                // Allow H2 console iframe in dev.
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }
}
