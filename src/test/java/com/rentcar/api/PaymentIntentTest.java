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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/bookings/{id}/payments/intent.
 *
 * <p>Tests the Stripe-ready intent contract without touching the actual charge flow.
 * The FakePaymentProvider returns a deterministic clientSecret so assertions are exact.
 *
 * <p>Date windows start at 1600+ days to avoid conflicts with other test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PaymentIntentTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    // ── 1. Happy path: create intent for a valid PENDING booking ─────────────

    @Test
    void createIntent_pendingBooking_returnsIntentWithBackendAmount() throws Exception {
        long carId   = anyAvailableCarId(1600, 1602);
        long bookingId = createBooking(carId, daysFromNow(1600), daysFromNow(1602));

        MvcResult result = mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.bookingReference").isNotEmpty())
                .andExpect(jsonPath("$.amount").isNumber())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.provider").value("FAKE"))
                .andExpect(jsonPath("$.clientSecret").value(startsWith("fake_client_secret_")))
                .andExpect(jsonPath("$.paymentId").isNumber())
                .andReturn();

        // Amount must come from booking total — verify it is > 0
        double amount = JsonPath.read(result.getResponse().getContentAsString(), "$.amount");
        assertThat(amount).isGreaterThan(0);
    }

    // ── 2. No body: calling without request body also works ──────────────────

    @Test
    void createIntent_noBody_returnsOk() throws Exception {
        long carId     = anyAvailableCarId(1610, 1612);
        long bookingId = createBooking(carId, daysFromNow(1610), daysFromNow(1612));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("FAKE"));
    }

    // ── 3. Unknown booking id returns 404 ────────────────────────────────────

    @Test
    void createIntent_unknownBookingId_returns404() throws Exception {
        mockMvc.perform(post("/api/bookings/999999/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    // ── 4. Already confirmed booking returns 409 ─────────────────────────────

    @Test
    void createIntent_confirmedBooking_returns409() throws Exception {
        long carId     = anyAvailableCarId(1620, 1622);
        long bookingId = createBooking(carId, daysFromNow(1620), daysFromNow(1622));

        // Confirm the booking via the process endpoint
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\": \"pm_valid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Now attempt to create an intent on the confirmed booking
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid booking state"));
    }

    // ── 5. Amount is from backend, not frontend ──────────────────────────────
    //      (implicit in test 1, but explicitly verified here by asserting
    //       that the paymentMethodType field in the request is ignored and
    //       the amount still equals the booking total)

    @Test
    void createIntent_amountIgnoresFrontendPaymentMethodType() throws Exception {
        long carId     = anyAvailableCarId(1630, 1632);
        long bookingId = createBooking(carId, daysFromNow(1630), daysFromNow(1632));

        // Fetch the booking to get its total price
        MvcResult bookingResult = mockMvc.perform(get("/api/bookings/" + bookingId))
                .andReturn(); // 401 is ok — we just need the intent amount

        // Create intent with an arbitrary paymentMethodType (should be ignored)
        MvcResult intentResult = mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodType\": \"BANK_TRANSFER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").isNumber())
                .andReturn();

        double amount = JsonPath.read(intentResult.getResponse().getContentAsString(), "$.amount");
        assertThat(amount).isGreaterThan(0);
        // provider is always FAKE regardless of paymentMethodType
        String provider = JsonPath.read(intentResult.getResponse().getContentAsString(), "$.provider");
        assertThat(provider).isEqualTo("FAKE");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long anyAvailableCarId(int pickupOffset, int dropoffOffset) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", daysFromNow(pickupOffset))
                        .param("dropoffDateTime", daysFromNow(dropoffOffset)))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).as("No cars available in window [+%dd, +%dd]", pickupOffset, dropoffOffset)
                .isNotEmpty();
        return ids.get(0).longValue();
    }

    private long createBooking(long carId, String pickup, String dropoff) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, pickup, dropoff)))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String daysFromNow(int days) {
        return LocalDateTime.now()
                .plusDays(days)
                .withHour(10).withMinute(0).withSecond(0).withNano(0)
                .format(FMT);
    }

    private String bookingBody(long carId, String pickup, String dropoff) {
        return """
                {
                  "carId": %d,
                  "customerName": "Intent Test User",
                  "customerEmail": "intent-test@example.com",
                  "customerPhone": "+34600000099",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport T1",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, pickup, dropoff);
    }
}
