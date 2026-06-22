package com.rentcar.api;

import com.rentcar.api.domain.user.AppRole;
import com.rentcar.api.domain.user.AppUser;
import com.rentcar.api.email.FakeEmailService;
import com.rentcar.api.repository.CustomerRepository;
import com.rentcar.api.repository.EmailOtpCodeRepository;
import com.rentcar.api.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
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
@ActiveProfiles("test")
@Transactional
public class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EmailOtpCodeRepository otpRepository;

    @Autowired
    private FakeEmailService fakeEmailService;

    @BeforeEach
    void clearEmails() {
        fakeEmailService.clearSentEmails();
    }

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

    @Test
    void profile_save_country_not_required_but_phone_is_required() throws Exception {
        AppUser u = AppUser.builder()
                .email("phone-profile@example.com")
                .role(AppRole.CUSTOMER)
                .provider(com.rentcar.api.domain.user.AuthProvider.LOCAL)
                .customerNumber("TEST-0002")
                .profileComplete(false)
                .build();
        userRepository.save(u);

        mockMvc.perform(post("/api/auth/profile")
                        .with(user("phone-profile@example.com").roles("CUSTOMER"))
                        .sessionAttr("APP_USER_EMAIL", u.getEmail())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Pat","lastName":"Lee","phoneCountryCode":"+34","phoneNumber":"600 000 000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.phoneCountryCode").value("+34"))
                .andExpect(jsonPath("$.phoneNumber").value("600000000"));
    }

    @Test
    void request_code_success_does_not_reveal_account_state() throws Exception {
        mockMvc.perform(post("/api/auth/email/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" New.User@Example.COM \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CODE_SENT"));

        org.assertj.core.api.Assertions.assertThat(fakeEmailService.getSentLoginOtpEmails()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(fakeEmailService.getSentLoginOtpEmails().getFirst().customerEmail())
                .isEqualTo("new.user@example.com");
    }

    @Test
    void verify_valid_code_for_existing_complete_customer_logs_in() throws Exception {
        AppUser u = AppUser.builder()
                .email("existing-otp@example.com")
                .firstName("Existing")
                .lastName("User")
                .phoneCountryCode("+34")
                .phoneNumber("600000000")
                .role(AppRole.CUSTOMER)
                .provider(com.rentcar.api.domain.user.AuthProvider.LOCAL)
                .customerNumber("TEST-0003")
                .profileComplete(true)
                .build();
        userRepository.save(u);

        requestCode("existing-otp@example.com");
        String code = fakeEmailService.getSentLoginOtpEmails().getFirst().code();

        mockMvc.perform(post("/api/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"existing-otp@example.com","code":"%s"}
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGGED_IN"));
    }

    @Test
    void verify_valid_code_for_new_email_returns_profile_required() throws Exception {
        requestCode("fresh-otp@example.com");
        String code = fakeEmailService.getSentLoginOtpEmails().getFirst().code();

        mockMvc.perform(post("/api/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"fresh-otp@example.com","code":"%s"}
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROFILE_REQUIRED"))
                .andExpect(jsonPath("$.profileToken").isString());
    }

    @Test
    void complete_profile_creates_customer_and_logs_in_without_duplicate_email() throws Exception {
        requestCode("complete-otp@example.com");
        String code = fakeEmailService.getSentLoginOtpEmails().getFirst().code();
        String profileToken = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/auth/email/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"complete-otp@example.com","code":"%s"}
                                        """.formatted(code)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.profileToken");

        mockMvc.perform(post("/api/auth/email/complete-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileToken":"%s","firstName":"Jane","lastName":"Doe","phoneCountryCode":"+34","phoneNumber":"600 000 000"}
                                """.formatted(profileToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGGED_IN"));

        org.assertj.core.api.Assertions.assertThat(userRepository.findByEmail("complete-otp@example.com")).isPresent();
        org.assertj.core.api.Assertions.assertThat(customerRepository.findByEmail("complete-otp@example.com")).isPresent();

        mockMvc.perform(post("/api/auth/email/complete-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileToken":"%s","firstName":"Jane","lastName":"Doe","phoneCountryCode":"+34","phoneNumber":"600 000 000"}
                                """.formatted(profileToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalid_code_fails_and_repeated_attempts_consume_code() throws Exception {
        requestCode("invalid-otp@example.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/email/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"invalid-otp@example.com\",\"code\":\"000000\"}"))
                    .andExpect(status().isBadRequest());
        }

        String validCode = fakeEmailService.getSentLoginOtpEmails().getFirst().code();
        mockMvc.perform(post("/api/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid-otp@example.com","code":"%s"}
                                """.formatted(validCode)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expired_code_fails() throws Exception {
        requestCode("expired-otp@example.com");
        var otp = otpRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc("expired-otp@example.com").orElseThrow();
        otp.setExpiresAt(java.time.Instant.now().minusSeconds(1));
        otpRepository.saveAndFlush(otp);

        String code = fakeEmailService.getSentLoginOtpEmails().getFirst().code();
        mockMvc.perform(post("/api/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"expired-otp@example.com","code":"%s"}
                                """.formatted(code)))
                .andExpect(status().isBadRequest());
    }

    private void requestCode(String email) throws Exception {
        mockMvc.perform(post("/api/auth/email/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }
}
