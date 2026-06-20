package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /api/admin/bookings.
 *
 * Uses far-future date windows (990+) to avoid conflicts with other tests
 * sharing the same H2 in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminBookingsTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    // ── Security ──────────────────────────────────────────────────────────────

    @Test
    void listBookings_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void listBookings_withWrongCredentials_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listBookings_withAdminCredentials_returnsOkAndArray() throws Exception {
        mockMvc.perform(get("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void bookingsFrontend_usesDetailModalInsteadOfRawApiLink() throws Exception {
        mockMvc.perform(get("/admin/bookings.html")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("booking-detail-overlay")))
                .andExpect(content().string(containsString("admin-bookings.js")));

        mockMvc.perform(get("/js/admin-bookings.js")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-booking-detail-id")))
                .andExpect(content().string(not(containsString("href=\"/api/admin/bookings/"))))
                .andExpect(content().string(not(containsString("target=\"_blank\""))));
    }

    // ── Response shape ────────────────────────────────────────────────────────

    @Test
    void listBookings_responseContainsExpectedFields() throws Exception {
        // Create a booking so we have at least one item with known shape.
        long bookingId = createBooking(daysFromNow(990), daysFromNow(992));

        MvcResult result = mockMvc.perform(get("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        List<Object> items = JsonPath.read(result.getResponse().getContentAsString(), "$");
        assertThat(items).isNotEmpty();

        // Verify the first item (newest) has all required fields.
        String json = result.getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(json, "$[0].id")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].bookingReference")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].status")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].customerName")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].customerEmail")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].carBrand")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].carModel")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].pickupDateTime")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].dropoffDateTime")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].totalPrice")).isNotNull();
        assertThat(json).contains(
                "\"source\"",
                "\"rentalCharge\"",
                "\"oneWayFee\"",
                "\"premiumLocationFee\"",
                "\"tax\"",
                "\"addonCharge\"",
                "\"bookingOptionType\"",
                "\"bookingOptionDailyFee\"",
                "\"cancellationPolicyType\"");
        // paymentStatus may be null before a payment intent is created — field must be present.
        assertThat(json).contains("\"paymentStatus\"");
    }

    @Test
    void detailBooking_responseContainsPricingAndPolicySnapshot() throws Exception {
        long bookingId = createBooking(daysFromNow(991), daysFromNow(992));

        mockMvc.perform(get("/api/admin/bookings/{id}", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) bookingId))
                .andExpect(jsonPath("$.rentalDays").isNumber())
                .andExpect(jsonPath("$.baseDailyPrice").isNumber())
                .andExpect(jsonPath("$.effectiveDailyPrice").isNumber())
                .andExpect(jsonPath("$.discountPercentage").isNumber())
                .andExpect(jsonPath("$.rentalCharge").isNumber())
                .andExpect(jsonPath("$.oneWayFee").isNumber())
                .andExpect(jsonPath("$.premiumLocationFee").isNumber())
                .andExpect(jsonPath("$.tax").isNumber())
                .andExpect(jsonPath("$.addonCharge").isNumber())
                .andExpect(jsonPath("$.bookingOptionType").value("BEST_PRICE"))
                .andExpect(jsonPath("$.bookingOptionDailyFee").value(0.0))
                .andExpect(jsonPath("$.cancellationPolicyType").value("STRICT"));
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    void listBookings_newestBookingAppearsFirst() throws Exception {
        long firstBookingId  = createBooking(daysFromNow(993), daysFromNow(995));
        long secondBookingId = createBooking(daysFromNow(996), daysFromNow(998));

        MvcResult result = mockMvc.perform(get("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        int idxFirst  = ids.indexOf((int) firstBookingId);
        int idxSecond = ids.indexOf((int) secondBookingId);

        assertThat(idxSecond).isLessThan(idxFirst);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long createBooking(LocalDateTime pickup, LocalDateTime dropoff) throws Exception {
        long carId = anyAvailableCarId(pickup, dropoff);
        String body = """
                {
                  "carId": %d,
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Test Location",
                  "dropoffLocation": "Test Location",
                  "customerName": "Admin Test",
                  "customerEmail": "admintest@example.com",
                  "customerPhone": "+34600000000",
                  "mileageOption": "INCLUDED"
                }
                """.formatted(carId, FMT.format(pickup), FMT.format(dropoff));

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long anyAvailableCarId(LocalDateTime pickup, LocalDateTime dropoff) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", FMT.format(pickup))
                        .param("dropoffDateTime", FMT.format(dropoff)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).as("No available cars for test date range").isNotEmpty();
        return ids.get(0);
    }

    private static LocalDateTime daysFromNow(int days) {
        return LocalDateTime.now().plusDays(days).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }
}
