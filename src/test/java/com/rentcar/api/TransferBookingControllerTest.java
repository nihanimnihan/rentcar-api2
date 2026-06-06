package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Disabled;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/transfer/bookings.
 *
 * Uses far-future date windows starting at 800+ days to avoid conflicts with
 * other tests sharing the same H2 in-memory database (BookingTransactionTest
 * uses up to 730 days, CarSearchAvailabilityTest up to 640 days).
 *
 * The seeded RIDE category has one car (BMW X1 SDrive18d, hourlyPrice=95.00,
 * seats=3). Tests that exhaust availability use unique day windows.
 */
@Disabled("Airport transfer is out of current MVP scope")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TransferBookingControllerTest {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private MockMvc mockMvc;

    // ── 1. Booking created successfully ──────────────────────────────────────

    @Test
    void createTransferBooking_validRequest_returns200WithConfirmation() throws Exception {
        Long categoryId = rideCategoryId(800);

        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(categoryId, dateAt(800), 2, 2, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerEmail").value("transfer-test@example.com"))
                .andExpect(jsonPath("$.categoryCode").isString())
                .andExpect(jsonPath("$.assignedCarBrand").isString())
                .andExpect(jsonPath("$.assignedCarModel").isString())
                .andExpect(jsonPath("$.durationHours").value(2))
                .andExpect(jsonPath("$.passengers").value(2))
                .andExpect(jsonPath("$.totalPrice").isNumber())
                .andExpect(jsonPath("$.hourlyPrice").isNumber());
    }

    // ── 2. Response contains all confirmation details ─────────────────────────

    @Test
    void createTransferBooking_responseContainsAllConfirmationFields() throws Exception {
        Long categoryId = rideCategoryId(810);

        MvcResult result = mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(categoryId, dateAt(810), 3, 1, "Aisle seat preferred")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat((Number) JsonPath.read(body, "$.id")).isNotNull();
        assertThat((String)  JsonPath.read(body, "$.status")).isEqualTo("PENDING");
        assertThat((String)  JsonPath.read(body, "$.categoryCode")).isNotEmpty();
        assertThat((String)  JsonPath.read(body, "$.categoryName")).isNotEmpty();
        assertThat((String)  JsonPath.read(body, "$.assignedCarBrand")).isNotEmpty();
        assertThat((String)  JsonPath.read(body, "$.assignedCarModel")).isNotEmpty();
        assertThat((Number)  JsonPath.read(body, "$.totalPrice")).isNotNull();
        assertThat((String)  JsonPath.read(body, "$.notes")).isEqualTo("Aisle seat preferred");
    }

    // ── 3. Assigned car belongs to requested category ────────────────────────

    @Test
    void createTransferBooking_assignedCar_belongsToRequestedCategory() throws Exception {
        Long categoryId = rideCategoryId(820);

        MvcResult result = mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(categoryId, dateAt(820), 2, 1, null)))
                .andExpect(status().isOk())
                .andReturn();

        String returnedCode = JsonPath.read(result.getResponse().getContentAsString(), "$.categoryCode");
        assertThat(returnedCode).isEqualTo("RIDE");
    }

    // ── 4. Overlapping transfer booking blocks availability ───────────────────

    @Test
    void createTransferBooking_overlappingBooking_returns409() throws Exception {
        Long categoryId = rideCategoryId(830);
        String pickup = dateAt(830);

        // First booking must succeed
        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(categoryId, pickup, 3, 1, null)))
                .andExpect(status().isOk());

        // Second booking in the same window — RIDE has only one car
        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(categoryId, pickup, 3, 1, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("No chauffeur car available"));
    }

    // ── 5. Passenger count exceeding category seats is rejected ───────────────

    @Test
    void createTransferBooking_passengersExceedSeats_returns400() throws Exception {
        // RIDE has 3 seats — 99 passengers must fail
        Long categoryId = rideCategoryId(840);

        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(categoryId, dateAt(840), 2, 99, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid transfer request"));
    }

    // ── 6. Invalid category ID returns 404 ────────────────────────────────────

    @Test
    void createTransferBooking_invalidCategoryId_returns404() throws Exception {
        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(99999L, dateAt(850), 2, 1, null)))
                .andExpect(status().isNotFound());
    }

    // ── 7. Missing required fields returns 400 with field names ──────────────

    @Test
    void createTransferBooking_missingRequiredFields_returns400WithFieldNames() throws Exception {
        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.message").value(containsString("categoryId must not be null")))
                .andExpect(jsonPath("$.message").value(containsString("customerName must not be blank")));
    }

    @Test
    void createTransferBooking_missingCategoryId_messageIncludesFieldName() throws Exception {
        String body = "{"
                + "\"customerName\": \"Test User\","
                + "\"customerEmail\": \"test@example.com\","
                + "\"customerPhone\": \"+34600000001\","
                + "\"pickupDateTime\": \"2030-01-01T10:00\","
                + "\"durationHours\": 2"
                // categoryId intentionally omitted
                + "}";
        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.message").value(containsString("categoryId must not be null")));
    }

    // ── 8. Endpoint is public — no auth required ──────────────────────────────

    @Test
    void createTransferBooking_isPublic_noAuthRequired() throws Exception {
        // Empty body → 400 validation error, NOT 401 auth error.
        // A 401 would mean security rejected it before reaching the controller.
        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── 9. Canonical "passengerCount" key maps to passengers in response ─────

    @Test
    void createTransferBooking_passengerCount2_responsePassengersEquals2() throws Exception {
        Long categoryId = rideCategoryId(860);
        String body = "{"
                + "\"customerName\": \"Passenger Test\","
                + "\"customerEmail\": \"pax@example.com\","
                + "\"customerPhone\": \"+34600000002\","
                + "\"pickupDateTime\": \"" + dateAt(860) + "\","
                + "\"durationHours\": 2,"
                + "\"categoryId\": " + categoryId + ","
                + "\"passengerCount\": 2"
                + "}";
        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passengers").value(2));
    }

    // ── 10. Backward-compat "passengers" key also maps correctly ─────────────

    @Test
    void createTransferBooking_legacyPassengersKey_responsePassengersEquals2() throws Exception {
        Long categoryId = rideCategoryId(865);
        // Frontend may still send "passengers" — accepted as alias
        String body = "{"
                + "\"customerName\": \"Legacy Test\","
                + "\"customerEmail\": \"legacy@example.com\","
                + "\"customerPhone\": \"+34600000003\","
                + "\"pickupDateTime\": \"" + dateAt(865) + "\","
                + "\"durationHours\": 2,"
                + "\"categoryId\": " + categoryId + ","
                + "\"passengers\": 2"
                + "}";
        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passengers").value(2));
    }

    // ── 11. passengerCount exceeding category seats returns 400, not 409 ─────

    @Test
    void createTransferBooking_passengerCountExceedsSeats_returns400NotConflict() throws Exception {
        Long categoryId = rideCategoryId(870);
        // RIDE has 3 seats — 99 must fail with validation error before reaching car search
        String body = "{"
                + "\"customerName\": \"Overflow Test\","
                + "\"customerEmail\": \"overflow@example.com\","
                + "\"customerPhone\": \"+34600000004\","
                + "\"pickupDateTime\": \"" + dateAt(870) + "\","
                + "\"durationHours\": 2,"
                + "\"categoryId\": " + categoryId + ","
                + "\"passengerCount\": 99"
                + "}";
        mockMvc.perform(post("/api/transfer/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid transfer request"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the RIDE category ID by calling the offers endpoint.
     * Each test uses a unique day offset so RIDE's single car is not exhausted
     * by a previous test in the same shared H2 database.
     */
    private Long rideCategoryId(int dayOffset) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transfer/offers")
                        .param("pickupDateTime", dateAt(dayOffset))
                        .param("durationHours", "2"))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(
                result.getResponse().getContentAsString(), "$[?(@.code=='RIDE')].categoryId");
        assertThat(ids)
                .as("RIDE category not available at +%d days — check seed data", dayOffset)
                .isNotEmpty();
        return ids.get(0).longValue();
    }

    /** ISO-truncated-to-minutes datetime for the given day offset at 10:00. */
    private String dateAt(int dayOffset) {
        return LocalDateTime.now()
                .plusDays(dayOffset)
                .withHour(10).withMinute(0).withSecond(0).withNano(0)
                .format(FMT);
    }

    private String bookingBody(Long categoryId, String pickup, int durationHours,
                               Integer passengers, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"customerName\": \"Transfer Test\",");
        sb.append("\"customerEmail\": \"transfer-test@example.com\",");
        sb.append("\"customerPhone\": \"+34600000077\",");
        sb.append("\"pickupDateTime\": \"").append(pickup).append("\",");
        sb.append("\"durationHours\": ").append(durationHours).append(",");
        sb.append("\"categoryId\": ").append(categoryId);
        if (passengers != null) {
            sb.append(",\"passengerCount\": ").append(passengers);
        }
        if (notes != null) {
            sb.append(",\"notes\": \"").append(notes).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}
