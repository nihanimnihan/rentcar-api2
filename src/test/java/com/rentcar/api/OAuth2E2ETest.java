package com.rentcar.api;

import com.rentcar.api.domain.user.AppRole;
import com.rentcar.api.domain.user.AppUser;
import com.rentcar.api.repository.AppUserRepository;
import com.rentcar.api.security.OAuth2LoginSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.security.oauth2.client.registration.google.client-id=test","spring.security.oauth2.client.registration.google.client-secret=test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class OAuth2E2ETest {

    @Autowired
    private OAuth2LoginSuccessHandler successHandler;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void google_registers_new_user_and_redirects_to_profile_step() throws Exception {
        String email = "e2e-new@example.com";
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", email);
        attrs.put("given_name", "New");
        attrs.put("family_name", "User");

        DefaultOAuth2User principal = new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attrs,
                "email");

        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        // set returnTo in session
        req.getSession(true).setAttribute("OAUTH2_RETURN_TO", "/index.html");

        successHandler.onAuthenticationSuccess(req, res, auth);

        // Expect redirect to signup profile step with encoded returnTo
        String redirected = res.getRedirectedUrl();
        assertThat(redirected).isNotNull();
        assertThat(redirected).startsWith("/signup.html?step=profile&returnTo=");

        // Verify user created in DB with profileComplete=false and country null
        var maybe = userRepository.findByEmail(email);
        assertThat(maybe).isPresent();
        AppUser u = maybe.get();
        assertThat(u.getFirstName()).isEqualTo("New");
        assertThat(u.getLastName()).isEqualTo("User");
        assertThat(u.getCountry()).isNull();
        assertThat(u.isProfileComplete()).isFalse();
    }

    @Test
    void google_existing_user_does_not_get_overwritten_and_redirects_to_returnTo_when_complete() throws Exception {
        String email = "e2e-exist@example.com";
        // create existing user with complete profile
        AppUser existing = AppUser.builder()
                .email(email)
                .firstName("Alice")
                .lastName("Smith")
                .country("TR")
                .phoneCountryCode("+90")
                .phoneNumber("5550000000")
                .role(AppRole.CUSTOMER)
                .provider(com.rentcar.api.domain.user.AuthProvider.LOCAL)
                .customerNumber("EXIST-1")
                .profileComplete(true)
                .build();
        userRepository.save(existing);

        // Google returns different names — should NOT overwrite existing filled fields
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", email);
        attrs.put("given_name", "Bob");
        attrs.put("family_name", "Jones");

        DefaultOAuth2User principal = new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attrs,
                "email");
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        req.getSession(true).setAttribute("OAUTH2_RETURN_TO", "/dashboard");

        successHandler.onAuthenticationSuccess(req, res, auth);

        String redirected = res.getRedirectedUrl();
        assertThat(redirected).isEqualTo("/dashboard");

        var maybe = userRepository.findByEmail(email);
        assertThat(maybe).isPresent();
        AppUser u = maybe.get();
        // values should remain as originally set
        assertThat(u.getFirstName()).isEqualTo("Alice");
        assertThat(u.getLastName()).isEqualTo("Smith");
        assertThat(u.getCountry()).isEqualTo("TR");
        assertThat(u.getPhoneCountryCode()).isEqualTo("+90");
        assertThat(u.getPhoneNumber()).isEqualTo("5550000000");
        assertThat(u.isProfileComplete()).isTrue();
    }
}
