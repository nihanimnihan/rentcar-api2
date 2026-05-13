package com.rentcar.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for server-side price calculation in BookingService:
 * - All price fields are not null in BookingResponse
 * - Duration discount tiers are applied correctly
 * - Add-on totals are included and persisted
 * - Historical totals are immutable snapshots (not live prices)
 *
 * Each test uses a non-overlapping far-future date window so bookings
 * for car 1 do not conflict across tests within the shared H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingPricingIntegrationTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    // ── Field presence ──────────────────────────────────────────────────────────

    @Test
    void bookingResponse_priceFieldsAreNotNull() throws Exception {
        // Window: +30 to +31 (1 day, non-overlapping with other tests)
        String body = bookingBody(1, daysFromNow(30), daysFromNow(31));
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseDailyPrice").exists())
                .andExpect(jsonPath("$.effectiveDailyPrice").exists())
                .andExpect(jsonPath("$.discountPercentage").exists())
                .andExpect(jsonPath("$.carRentalTotal").exists())
                .andExpect(jsonPath("$.addonTotal").exists())
                .andExpect(jsonPath("$.totalPrice").exists())
                .andExpect(jsonPath("$.rentalDays").exists())
                .andExpect(jsonPath("$.includedKmSnapshot").isNumber())
                .andExpect(jsonPath("$.unlimitedKmPriceSnapshot").isNumber());
    }

    @Test
    void oneDayBooking_includedKmSnapshot_is300() throws Exception {
        String body = bookingBody(1, daysFromNow(32), daysFromNow(33));
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalDays").value(1))
                .andExpect(jsonPath("$.includedKmSnapshot").value(300));
    }

    @Test
    void sevenDayBooking_includedKmSnapshot_is2100() throws Exception {
        String body = bookingBody(1, daysFromNow(160), daysFromNow(167));
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalDays").value(7))
                .andExpect(jsonPath("$.includedKmSnapshot").value(2100));
    }

    @Test
    void unlimitedKmPriceSnapshot_isGreaterThanFloor() throws Exception {
        String body = bookingBody(1, daysFromNow(170), daysFromNow(171));
        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        double unlimitedKmPrice = ((Number) com.jayway.jsonpath.JsonPath
                .read(result.getResponse().getContentAsString(), "$.unlimitedKmPriceSnapshot"))
                .doubleValue();
        // Must be >= 4.70 (the configured floor)
        assertThat(unlimitedKmPrice).isGreaterThanOrEqualTo(4.70);
    }

    // ── No discount for 1–2 days ────────────────────────────────────────────────

    @Test
    void oneDayRental_zeroDiscount() throws Exception {
        // Window: +40 to +41
        String body = bookingBody(1, daysFromNow(40), daysFromNow(41));
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalDays").value(1))
                .andExpect(jsonPath("$.discountPercentage").value(0));
    }

    // ── 5% discount for 3–6 days ────────────────────────────────────────────────

    @Test
    void threeDayRental_fivePercentDiscount() throws Exception {
        // Window: +50 to +53
        String body = bookingBody(1, daysFromNow(50), daysFromNow(53));
        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalDays").value(3))
                .andExpect(jsonPath("$.discountPercentage").value(5))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        double base = com.jayway.jsonpath.JsonPath.read(json, "$.baseDailyPrice");
        double effective = com.jayway.jsonpath.JsonPath.read(json, "$.effectiveDailyPrice");
        assertThat(effective).isEqualTo(base * 0.95, org.assertj.core.api.Assertions.within(0.01));
    }

    // ── 10% discount for 7–13 days ──────────────────────────────────────────────

    @Test
    void sevenDayRental_tenPercentDiscount() throws Exception {
        // Window: +60 to +67
        String body = bookingBody(1, daysFromNow(60), daysFromNow(67));
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalDays").value(7))
                .andExpect(jsonPath("$.discountPercentage").value(10));
    }

    // ── 15% discount for 14–27 days ─────────────────────────────────────────────

    @Test
    void fourteenDayRental_fifteenPercentDiscount() throws Exception {
        // Window: +80 to +94
        String body = bookingBody(1, daysFromNow(80), daysFromNow(94));
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalDays").value(14))
                .andExpect(jsonPath("$.discountPercentage").value(15));
    }

    // ── 25% discount for 28+ days ───────────────────────────────────────────────

    @Test
    void twentyEightDayRental_twentyFivePercentDiscount() throws Exception {
        // Window: +100 to +128
        String body = bookingBody(1, daysFromNow(100), daysFromNow(128));
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalDays").value(28))
                .andExpect(jsonPath("$.discountPercentage").value(25));
    }

    // ── Server-side price: frontend sending no price fields ──────────────────────

    @Test
    void totalPrice_isCalculatedServerSide_notFromFrontend() throws Exception {
        // Window: +140 to +141 — body contains no price field at all
        String body = bookingBody(1, daysFromNow(140), daysFromNow(141));
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").isNumber())
                .andExpect(jsonPath("$.baseDailyPrice").isNumber());
    }

    // ── Add-on total included in totalPrice ─────────────────────────────────────

    @Test
    void bookingWithAddons_addonTotalReflectedInTotalPrice() throws Exception {
        // Fetch a real addon id from the public API
        String addonsResponse = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/addons/active"))
                .andReturn().getResponse().getContentAsString();

        // JsonPath returns Integer for small numbers — use Number to avoid ClassCast
        long addonId = ((Number) com.jayway.jsonpath.JsonPath.read(addonsResponse, "$[0].id")).longValue();

        // Window: +150 to +151 — use car 2 to avoid any overlap with car-1 tests
        String body = bookingBodyWithAddon(2, daysFromNow(150), daysFromNow(151), addonId);

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addons").isArray())
                .andExpect(jsonPath("$.addons[0].addonId").exists())
                .andExpect(jsonPath("$.addons[0].lineTotal").isNumber())
                .andExpect(jsonPath("$.addons[0].pricingTypeSnapshot").isString())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        double addonTotal = ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.addonTotal")).doubleValue();
        double totalPrice = ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.totalPrice")).doubleValue();
        double carRentalTotal = ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.carRentalTotal")).doubleValue();

        assertThat(addonTotal).isGreaterThan(0.0);
        // totalPrice = carRentalTotal + addonTotal  (tax=0, same location → no fees)
        assertThat(totalPrice).isEqualTo(carRentalTotal + addonTotal, org.assertj.core.api.Assertions.within(0.02));
    }

    // ── Mileage option: UNLIMITED ────────────────────────────────────────────────

    @Test
    void unlimitedMileage_isPersistedInBookingResponse() throws Exception {
        String body = bookingBodyWithMileage(1, daysFromNow(200), daysFromNow(201), "UNLIMITED");
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileageOption").value("UNLIMITED"));
    }

    @Test
    void unlimitedMileage_totalIsHigherThanIncluded() throws Exception {
        // Same car, same duration (1 day) — different date windows to avoid overlap
        String includedBody = bookingBodyWithMileage(1, daysFromNow(210), daysFromNow(211), "INCLUDED");
        String unlimitedBody = bookingBodyWithMileage(1, daysFromNow(212), daysFromNow(213), "UNLIMITED");

        MvcResult includedResult = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(includedBody))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult unlimitedResult = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unlimitedBody))
                .andExpect(status().isOk())
                .andReturn();

        double includedTotal = ((Number) com.jayway.jsonpath.JsonPath
                .read(includedResult.getResponse().getContentAsString(), "$.totalPrice"))
                .doubleValue();
        double unlimitedTotal = ((Number) com.jayway.jsonpath.JsonPath
                .read(unlimitedResult.getResponse().getContentAsString(), "$.totalPrice"))
                .doubleValue();

        assertThat(unlimitedTotal).isGreaterThan(includedTotal);
    }

    @Test
    void noMileageOption_defaultsToIncluded() throws Exception {
        // body without mileageOption field at all
        String body = bookingBody(1, daysFromNow(220), daysFromNow(221));
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileageOption").value("INCLUDED"));
    }

    // ── Car search date validation ───────────────────────────────────────────────

    @Test
    void searchCars_withPastPickupDate_returns400() throws Exception {
        String past = LocalDateTime.now().minusDays(1).format(FMT);
        String future = LocalDateTime.now().plusDays(2).format(FMT);
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/cars/search")
                        .param("pickupDateTime", past)
                        .param("dropoffDateTime", future))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid search dates"));
    }

    @Test
    void searchCars_withDropoffBeforePickup_returns400() throws Exception {
        String pickup  = daysFromNow(5);
        String dropoff = daysFromNow(3);
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/cars/search")
                        .param("pickupDateTime", pickup)
                        .param("dropoffDateTime", dropoff))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid search dates"));
    }

    @Test
    void searchCars_withValidDates_returns200() throws Exception {
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/cars/search")
                        .param("pickupDateTime", daysFromNow(10))
                        .param("dropoffDateTime", daysFromNow(12)))
                .andExpect(status().isOk());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private String daysFromNow(int days) {
        return LocalDateTime.now().plusDays(days).withHour(10).withMinute(0).withSecond(0).withNano(0).format(FMT);
    }

    private String bookingBody(long carId, String pickup, String dropoff) {
        return """
                {
                  "carId": %d,
                  "customerName": "Test User",
                  "customerEmail": "pricing-test@example.com",
                  "customerPhone": "+34600000010",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "City Centre",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, pickup, dropoff);
    }

    private String bookingBodyWithAddon(long carId, String pickup, String dropoff, long addonId) {
        return """
                {
                  "carId": %d,
                  "customerName": "Test User",
                  "customerEmail": "addon-pricing@example.com",
                  "customerPhone": "+34600000011",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "City Centre",
                  "dropoffLocation": "City Centre",
                  "addonIds": [%d]
                }
                """.formatted(carId, pickup, dropoff, addonId);
    }

    private String bookingBodyWithMileage(long carId, String pickup, String dropoff, String mileageOption) {
        return """
                {
                  "carId": %d,
                  "customerName": "Test User",
                  "customerEmail": "mileage-test@example.com",
                  "customerPhone": "+34600000020",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "City Centre",
                  "dropoffLocation": "City Centre",
                  "mileageOption": "%s"
                }
                """.formatted(carId, pickup, dropoff, mileageOption);
    }
}
