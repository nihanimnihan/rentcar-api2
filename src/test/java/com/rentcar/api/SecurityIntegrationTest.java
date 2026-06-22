package com.rentcar.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the security rules defined in SecurityConfig:
 * - Public endpoints are reachable without credentials
 * - Protected endpoints return 401 without credentials
 * - Protected endpoints return 401 with wrong credentials
 * - Protected endpoints return 200 (or non-401/403) with valid admin credentials
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    // Credentials must match application-dev.yaml → rentcar.admin.*
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";
    private static final String WRONG_PASS = "wrong-password";

    @Autowired
    private MockMvc mockMvc;

    // ── Public endpoints ───────────────────────────────────────────────────────

    @Test
    void carSearch_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/cars/search"))
                .andExpect(status().isOk());
    }

    @Test
    void bookingCreation_isPublic_noAuthRequired() throws Exception {
        // Intentionally send an empty body — we expect 400 (validation), not 401.
        // A 401 would mean security blocked it before reaching the controller.
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Protected endpoints — no credentials ──────────────────────────────────

    @Test
    void getPayments_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void getBookings_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/bookings/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ── Protected endpoints — wrong credentials ───────────────────────────────

    @Test
    void getPayments_withWrongCredentials_returns401() throws Exception {
        mockMvc.perform(get("/api/payments")
                        .with(httpBasic(ADMIN_USER, WRONG_PASS)))
                .andExpect(status().isUnauthorized());
    }

    // ── Protected endpoints — valid admin credentials ─────────────────────────

    @Test
    void getBookings_withAdminCredentials_isNotRejected() throws Exception {
        // The booking doesn't exist (404) but that means security passed — not 401/403.
        mockMvc.perform(get("/api/bookings/99999")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isNotFound());
    }

    // ── Booking cancellation — explicitly admin-only ──────────────────────────

    @Test
    void cancelBooking_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/bookings/1/cancel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void adminPage_withoutAuth_redirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/bookings.html"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin-login.html"));
    }

    @Test
    void adminLoginPage_isPublic() throws Exception {
        mockMvc.perform(get("/admin-login.html"))
                .andExpect(status().isOk());
    }

    @Test
    void adminFormLogin_withValidCredentials_redirectsToDashboard() throws Exception {
        mockMvc.perform(formLogin("/admin-login")
                        .user("username", ADMIN_USER)
                        .password("password", ADMIN_PASS))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/bookings.html"));
    }

    @Test
    void adminFormLogin_withInvalidCredentials_redirectsToFriendlyError() throws Exception {
        mockMvc.perform(formLogin("/admin-login")
                        .user("username", ADMIN_USER)
                        .password("password", WRONG_PASS))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin-login.html?error=true"));
    }

    @Test
    void adminPage_withAdminRole_isAccessible() throws Exception {
        mockMvc.perform(get("/admin/bookings.html")
                        .with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void cancelBooking_withWrongCredentials_returns401() throws Exception {
        mockMvc.perform(post("/api/bookings/1/cancel")
                        .with(httpBasic(ADMIN_USER, WRONG_PASS)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelBooking_withAdminCredentials_isNotRejected() throws Exception {
        // Booking 99999 doesn't exist → 404, which means security passed.
        mockMvc.perform(post("/api/bookings/99999/cancel")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isNotFound());
    }

    // ── Public transfer endpoints ─────────────────────────────────────────────

    @Test
    void transferDurations_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/transfer/durations"))
                .andExpect(status().isOk());
    }

    @Test
    void transferOffers_isPublic_noAuthRequired() throws Exception {
        // Minimal required params; empty result is fine — we only verify security allows it.
        mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", "2030-01-01T10:00")
                        .param("durationHours", "2"))
                .andExpect(status().isOk());
    }

    @Test
    void addonsActive_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/addons/active"))
                .andExpect(status().isOk());
    }
}
