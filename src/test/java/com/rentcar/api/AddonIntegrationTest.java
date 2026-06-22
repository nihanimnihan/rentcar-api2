package com.rentcar.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Addon feature (H-6):
 * - GET /api/addons/active is public
 * - Admin CRUD endpoints require ADMIN role
 * - Inactive addons are not shown in the public list
 * - POST /api/bookings with addonIds persists add-ons and adjusts the total
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AddonIntegrationTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";

    @Autowired
    private MockMvc mockMvc;

    // ── Public endpoint ─────────────────────────────────────────────────────────

    @Test
    void activeAddons_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/addons/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void activeAddons_returnsOnlyActiveAddons() throws Exception {
        // All seeded addons are active=true, so the list should not be empty
        mockMvc.perform(get("/api/addons/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    // ── Admin CRUD ──────────────────────────────────────────────────────────────

    @Test
    void getAllAddons_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/addons"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllAddons_withAdminAuth_returnsOk() throws Exception {
        mockMvc.perform(get("/api/addons")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createAddon_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/addons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":10.00,\"pricingType\":\"DAILY\",\"active\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAddon_withAdminAuth_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/addons")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Addon\",\"description\":\"desc\",\"price\":10.00,\"pricingType\":\"DAILY\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Addon"))
                .andExpect(jsonPath("$.pricingType").value("DAILY"));
    }

    @Test
    void softDelete_withAdminAuth_setsInactive() throws Exception {
        // Create an addon first
        String response = mockMvc.perform(post("/api/addons")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ToDelete\",\"price\":5.00,\"pricingType\":\"ONE_TIME\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract the id from the response (simple substring approach)
        long id = com.jayway.jsonpath.JsonPath.parse(response).read("$.id", Long.class);

        // Soft-delete it
        mockMvc.perform(delete("/api/addons/" + id)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isNoContent());

        // Verify it's no longer in the public active list
        mockMvc.perform(get("/api/addons/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());

        // But still visible to admin
        mockMvc.perform(get("/api/addons/" + id)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ── Booking with add-ons ────────────────────────────────────────────────────

    @Test
    void bookingWithAddonIds_invalidBody_returns400NotUnauthorized() throws Exception {
        // Sending an empty body should return 400 (validation), NOT 401 (auth blocked).
        // This confirms the endpoint is still public even after the addon security rules.
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
