package com.rentcar.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@ConfigurationPropertiesScan
public class SecurityConfig {


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, com.rentcar.api.security.OAuth2LoginSuccessHandler successHandler, com.rentcar.api.security.CustomOAuth2UserService oauth2UserService, ClientRegistrationRepository clientRegistrationRepository, com.rentcar.api.security.SessionAuthenticationFilter sessionAuthenticationFilter) throws Exception {
        http
                /*
                 * CSRF disabled: keep disabled for now to avoid breaking existing JS fetch()
                 * used by guest checkout. OAuth2 login will use the session cookie but
                 * we rely on same-origin protections and internal-only returnTo handling
                 * to reduce risk. Review CSRF posture before production.
                 */
                .csrf(AbstractHttpConfigurer::disable)

                // Allow HttpSession for OAuth2 login flows; other APIs remain usable with or without sessions.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .httpBasic(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        // ── Static frontend resources ──────────────────────────────────────
                        .requestMatchers("/admin.html", "/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/", "/*.html").permitAll()
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
                        .requestMatchers(HttpMethod.POST, "/api/payments/stripe/webhook").permitAll()
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
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN","SUPER_ADMIN") // ADMIN+SUPER_ADMIN allowed
                        .requestMatchers("/api/payments/**").hasAnyRole("ADMIN","SUPER_ADMIN")

                        // ── Public auth endpoints
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/profile").authenticated()
                        // Allow logout to be called even if the session is expired so clients can always POST it.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()

                        // Allow OAuth2 authorization endpoints to be accessed without prior authentication
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/**", "/oauth2/authorize").permitAll()

                        // Booking reads and cancellation are admin-only.
                        // The public POST /api/bookings and POST /api/bookings/*/payments/process
                        // rules above already match before reaching this line.
                        .requestMatchers(HttpMethod.POST, "/api/bookings/*/cancel").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/bookings/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/customers/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers("/api/addons/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/cars/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/cars/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/cars/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/cars/**").hasAnyRole("ADMIN","SUPER_ADMIN")

                        .anyRequest().authenticated()
                )

                // JSON 403 for authenticated users without the ADMIN role and JSON 401 for API requests
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write(
                                    "{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        })
                        .authenticationEntryPoint((request, response, authException) -> {
                            // For API endpoints prefer JSON 401; for browser requests redirect to signup
                            String path = request instanceof HttpServletRequest ? ((HttpServletRequest) request).getRequestURI() : null;
                            if (path != null && path.startsWith("/api/")) {
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                            } else {
                                // Non-API: redirect to signup/login page
                                response.sendRedirect("/signup.html");
                            }
                        })
                )

                // Allow H2 console iframe in dev.
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )

                // Logout endpoint: use Spring Security's logout filter so it's idempotent and returns 200 even if session expired
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            var sess = request.getSession(false);
                            String sid = (sess != null) ? sess.getId() : "-";
                            org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).info("Logout request, sessionId={}", sid);
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            try {
                                response.getWriter().write("{}");
                            } catch (java.io.IOException ignored) {}
                        })
                )

                // OAuth2 login (Google)
                .oauth2Login(oauth -> oauth
                        .loginPage("/signup.html")
                        .authorizationEndpoint(authz -> authz.authorizationRequestResolver(
                                new GooglePromptAuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization")
                        ))
                        .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService))
                        .successHandler(successHandler)
                );

        // Register SessionAuthenticationFilter before the security context is used
        http.addFilterBefore(sessionAuthenticationFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService adminUserDetailsService(
            @Value("${rentcar.admin.username:admin}") String username,
            @Value("${rentcar.admin.password:change-me}") String password,
            PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles("ADMIN")
                        .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Resolver that adds prompt=select_account for Google authorization requests,
     * leaving other providers unchanged.
     */
    private static class GooglePromptAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
        private final DefaultOAuth2AuthorizationRequestResolver delegate;
        private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GooglePromptAuthorizationRequestResolver.class);

        public GooglePromptAuthorizationRequestResolver(ClientRegistrationRepository repo, String authorizationRequestBaseUri) {
            this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, authorizationRequestBaseUri);
        }

        @Override
        public OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest request) {
            OAuth2AuthorizationRequest req = delegate.resolve(request);
            if (req == null) return null;
            // Try to detect registrationId from request path param if possible (best-effort)
            String regId = (String) request.getAttribute("registrationId");
            if (regId == null) {
                // Fallback: try parse from URI like /oauth2/authorization/{registrationId}
                String uri = request.getRequestURI();
                if (uri != null) {
                    int idx = uri.lastIndexOf('/');
                    if (idx >= 0 && idx + 1 < uri.length()) regId = uri.substring(idx + 1);
                }
            }
            if ("google".equalsIgnoreCase(regId)) {
                var params = new java.util.LinkedHashMap<>(req.getAdditionalParameters());
                boolean already = params.containsKey("prompt");
                params.put("prompt", "select_account");
                OAuth2AuthorizationRequest out = OAuth2AuthorizationRequest.from(req).additionalParameters(params).build();
                log.info("GooglePromptAuthorizationRequestResolver: added prompt=select_account (alreadyPresent={}) for registrationId={}", already, regId);
                return out;
            }
            return req;
        }

        @Override
        public OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest request, String registrationId) {
            OAuth2AuthorizationRequest req = delegate.resolve(request, registrationId);
            if (req == null) return null;
            if ("google".equalsIgnoreCase(registrationId)) {
                var params = new java.util.LinkedHashMap<>(req.getAdditionalParameters());
                boolean already = params.containsKey("prompt");
                params.put("prompt", "select_account");
                OAuth2AuthorizationRequest out = OAuth2AuthorizationRequest.from(req).additionalParameters(params).build();
                log.info("GooglePromptAuthorizationRequestResolver: added prompt=select_account (alreadyPresent={}) for registrationId={}", already, registrationId);
                return out;
            }
            return req;
        }
    }

}
