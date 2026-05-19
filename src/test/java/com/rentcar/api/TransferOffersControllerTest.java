package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /api/transfer/offers.
 * Runs against the full Spring context with H2 + dev-profile seed data.
 * All requests use a far-future pickup date (2099) to guarantee no booking
 * conflicts with other tests sharing the same H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TransferOffersControllerTest {

    // yyyy-MM-dd'T'HH:mm — matches the controller's PICKUP_DT_FMT
    private static final String FUTURE_PICKUP = "2099-06-01T10:00";

    @Autowired
    private MockMvc mockMvc;

    // ── Security ─────────────────────────────────────────────────────────────────

    @Test
    void getOffers_isPublic_returns200WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", FUTURE_PICKUP)
                        .param("durationHours", "2"))
                .andExpect(status().isOk());
    }

    // ── JSON structure ────────────────────────────────────────────────────────────

    @Test
    void getOffers_responseIsJsonArray() throws Exception {
        mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", FUTURE_PICKUP)
                        .param("durationHours", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getOffers_firstResultContainsAllRequiredFields() throws Exception {
        mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", FUTURE_PICKUP)
                        .param("durationHours", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").exists())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].seats").exists())
                .andExpect(jsonPath("$[0].bags").exists())
                .andExpect(jsonPath("$[0].electric").exists())
                .andExpect(jsonPath("$[0].hourlyPriceFrom").isNumber())
                .andExpect(jsonPath("$[0].totalPrice").isNumber())
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    void getOffers_allReturnedCategoriesAreAvailable() throws Exception {
        // The service filters out unavailable categories, so none should appear in results
        mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", FUTURE_PICKUP)
                        .param("durationHours", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.available == false)]").isEmpty());
    }

    // ── Pricing ──────────────────────────────────────────────────────────────────

    @Test
    void getOffers_totalPriceEqualsHourlyPriceFromTimesHours() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", FUTURE_PICKUP)
                        .param("durationHours", "3"))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        double hourlyPriceFrom = ((Number) JsonPath.read(json, "$[0].hourlyPriceFrom")).doubleValue();
        double totalPrice = ((Number) JsonPath.read(json, "$[0].totalPrice")).doubleValue();

        assertThat(totalPrice).isEqualTo(hourlyPriceFrom * 3, org.assertj.core.api.Assertions.within(0.01));
    }

    // ── Passenger filter ─────────────────────────────────────────────────────────

    @Test
    void getOffers_passengers1_returnsNonEmptyList() throws Exception {
        // passengers=1 fits all seeded categories — expect at least one result
        mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", FUTURE_PICKUP)
                        .param("durationHours", "2")
                        .param("passengers", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    void getOffers_passengersExceedsAllCategorySeats_returnsEmptyList() throws Exception {
        // No seeded category has 100 seats — the filter must eliminate all results
        mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", FUTURE_PICKUP)
                        .param("durationHours", "2")
                        .param("passengers", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getOffers_higherPassengerCount_returnsFewerResults() throws Exception {
        // passengers=1 should yield more results than passengers=5
        // (categories with seats < 5 are dropped)
        MvcResult low = mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", FUTURE_PICKUP)
                        .param("durationHours", "2")
                        .param("passengers", "1"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult high = mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", FUTURE_PICKUP)
                        .param("durationHours", "2")
                        .param("passengers", "5"))
                .andExpect(status().isOk())
                .andReturn();

        int countLow = (int) JsonPath.read(low.getResponse().getContentAsString(), "$.length()");
        int countHigh = (int) JsonPath.read(high.getResponse().getContentAsString(), "$.length()");

        assertThat(countLow).isGreaterThan(countHigh);
    }
}
