package com.rentcar.api;

import com.rentcar.api.domain.user.AppRole;
import com.rentcar.api.domain.user.AppUser;
import com.rentcar.api.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties = {"spring.security.oauth2.client.registration.google.client-id=test","spring.security.oauth2.client.registration.google.client-secret=test"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void unauthenticated_me_returns_authenticated_false() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void logout_is_idempotent_and_returns_200_for_anonymous() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }

    @Test
    void profile_save_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(post("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profile_save_missing_fields_returns_400() throws Exception {
        // create a user so controller finds it
        AppUser u = AppUser.builder()
                .email("tester@example.com")
                .role(AppRole.CUSTOMER)
                .provider(com.rentcar.api.domain.user.AuthProvider.LOCAL)
                .customerNumber("TEST-0001")
                .profileComplete(false)
                .build();
        userRepository.save(u);

        mockMvc.perform(post("/api/auth/profile")
                        .with(user("tester@example.com").roles("CUSTOMER"))
                        .sessionAttr("APP_USER_EMAIL", u.getEmail())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
