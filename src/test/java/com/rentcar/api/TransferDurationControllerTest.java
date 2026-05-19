package com.rentcar.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /api/transfer/durations.
 * Runs against the full Spring context with an H2 in-memory database
 * seeded by SeedDataConfig (dev profile: 12 transfer durations, 1–12 hours).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TransferDurationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getDurations_isPublic_returns200WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/transfer/durations"))
                .andExpect(status().isOk());
    }

    @Test
    void getDurations_returnsAllSeededActiveDurations() throws Exception {
        mockMvc.perform(get("/api/transfer/durations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(12));
    }

    @Test
    void getDurations_responseContainsRequiredFields() throws Exception {
        mockMvc.perform(get("/api/transfer/durations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].hours").exists())
                .andExpect(jsonPath("$[0].includedKm").exists());
    }

    @Test
    void getDurations_orderedByDisplayOrderAscending() throws Exception {
        mockMvc.perform(get("/api/transfer/durations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hours").value(1))
                .andExpect(jsonPath("$[11].hours").value(12));
    }

    @Test
    void getDurations_includedKmIsHoursTimesThirty() throws Exception {
        mockMvc.perform(get("/api/transfer/durations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].includedKm").value(30))
                .andExpect(jsonPath("$[4].hours").value(5))
                .andExpect(jsonPath("$[4].includedKm").value(150));
    }

    @Test
    void getDurations_doesNotExposeInternalFields() throws Exception {
        // active and displayOrder are persistence concerns, not part of the public DTO
        mockMvc.perform(get("/api/transfer/durations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").doesNotExist())
                .andExpect(jsonPath("$[0].displayOrder").doesNotExist());
    }
}
