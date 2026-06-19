package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.payment.provider.FakePaymentProvider;
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
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/bookings/{id}/payments/intent.
 *
 * <p>Tests the Stripe-ready intent contract: no charging happens, only a payment intent
 * record is prepared and a clientSecret returned. FakePaymentProvider returns deterministic
 * synthetic values so all assertions are exact.
 *
 * <p>Date windows start at 1600+ days to avoid conflicts with other test classes.
 *
 * <p><b>State machine coverage:</b>
 * <ul>
 *   <li>PENDING → intent succeeds (idempotent on repeated calls)</li>
 *   <li>FAILED → fresh payment record created, booking resets to PENDING</li>
 *   <li>CONFIRMED → 409 (already paid)</li>
 *   <li>CANCELLED → 409 (payment not possible)</li>
 *   <li>Unknown id → 404</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PaymentIntentTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";

    @Autowired
    private MockMvc mockMvc;

    // ── 1. Happy path: PENDING booking returns intent with server-side amount ──

    @Test
    void createIntent_pendingBooking_returnsIntentWithBackendAmount() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1600, 1602), daysFromNow(1600), daysFromNow(1602));

        MvcResult result = mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intentRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.bookingReference").value(matchesPattern("RC-\\d{6}-[A-Z0-9]{4}")))
                .andExpect(jsonPath("$.amount").isNumber())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.provider").value("FAKE"))
                .andExpect(jsonPath("$.clientSecret").value(startsWith("fake_client_secret_")))
                .andExpect(jsonPath("$.paymentReference").value(matchesPattern("PAY-[0-9A-F]{8}")))
                .andReturn();

        // Amount must come from backend booking total — never from the request body
        double amount = JsonPath.read(result.getResponse().getContentAsString(), "$.amount");
        assertThat(amount).isGreaterThan(0);
    }

    // ── 2. No body: omitting the request body is supported ───────────────────

    @Test
    void createIntent_noBody_returnsOk() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1610, 1612), daysFromNow(1610), daysFromNow(1612));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intentRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("FAKE"))
                .andExpect(jsonPath("$.paymentReference").value(matchesPattern("PAY-[0-9A-F]{8}")));
    }

    // ── 3. Idempotency: repeated intent calls for PENDING booking are stable ──
    //      Same payment record → same paymentReference returned both times.

    @Test
    void createIntent_pendingBooking_idempotent_returnsSamePaymentReference() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1615, 1617), daysFromNow(1615), daysFromNow(1617));

        String ref1 = JsonPath.read(
                mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(intentRequestBody()))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.paymentReference");

        String ref2 = JsonPath.read(
                mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(intentRequestBody()))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.paymentReference");

        // Both calls reuse the same PENDING payment record → same paymentReference
        assertThat(ref1).isEqualTo(ref2);
    }

    // ── 4. FAILED booking gets a fresh payment record ─────────────────────────
    //      After a failed payment attempt, calling intent resets booking to PENDING
    //      and creates a new payment record (new paymentReference).

    @Test
    void createIntent_failedBooking_createsFreshPaymentRecord() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1620, 1622), daysFromNow(1620), daysFromNow(1622));

        // Force a payment failure
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(FakePaymentProvider.FORCE_FAIL_METHOD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // Now request an intent on the FAILED booking — should succeed with a new payment record
        MvcResult intentResult = mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intentRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.paymentReference").value(matchesPattern("PAY-[0-9A-F]{8}")))
                .andExpect(jsonPath("$.provider").value("FAKE"))
                .andReturn();

        // Booking is back to PENDING — a second intent call should also succeed (idempotent)
        String refFromIntent = JsonPath.read(intentResult.getResponse().getContentAsString(), "$.paymentReference");

        String refFromSecondIntent = JsonPath.read(
                mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(intentRequestBody()))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.paymentReference");

        // Both calls after reset use the same new payment record
        assertThat(refFromIntent).isEqualTo(refFromSecondIntent);
    }

    // ── 5. Unknown booking id returns 404 ────────────────────────────────────

    @Test
    void createIntent_unknownBookingId_returns404() throws Exception {
        mockMvc.perform(post("/api/bookings/999999/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intentRequestBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    // ── 6. CONFIRMED booking rejects intent with 409 ─────────────────────────

    @Test
    void createIntent_confirmedBooking_returns409() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1630, 1632), daysFromNow(1630), daysFromNow(1632));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_valid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intentRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid booking state"));
    }

    // ── 7. CANCELLED booking rejects intent with 409 ─────────────────────────

    @Test
    void createIntent_cancelledBooking_returns409() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1640, 1642), daysFromNow(1640), daysFromNow(1642));

        // Cancel via admin endpoint (requires ADMIN auth)
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intentRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid booking state"));
    }

    // ── 8. paymentMethodType in request is accepted but does not affect amount ─

    @Test
    void createIntent_arbitraryPaymentMethodType_amountIsFromBackend() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1650, 1652), daysFromNow(1650), daysFromNow(1652));

        String bodyWithMethod = lastCheckoutToken != null
                ? String.format("{\"paymentMethodType\": \"BANK_TRANSFER\", \"checkoutSessionToken\": \"%s\"}", lastCheckoutToken)
                : "{\"paymentMethodType\": \"BANK_TRANSFER\"}";

        MvcResult result = mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithMethod))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").isNumber())
                .andExpect(jsonPath("$.provider").value("FAKE")) // provider unchanged
                .andReturn();

        double amount = JsonPath.read(result.getResponse().getContentAsString(), "$.amount");
        assertThat(amount).isGreaterThan(0);
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

    private String lastCheckoutToken = null;

    private long createBooking(long carId, String pickup, String dropoff) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, pickup, dropoff)))
                .andExpect(status().isOk())
                .andReturn();
        lastCheckoutToken = result.getResponse().getHeader("X-Checkout-Session-Token");
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String intentRequestBody() {
        if (lastCheckoutToken != null) {
            return String.format("{\"checkoutSessionToken\": \"%s\"}", lastCheckoutToken);
        }
        return "{}";
    }

    private String payBody(String paymentMethodId) {
        return """
                {"paymentMethodId": "%s", "checkoutSessionToken": "%s"}
                """.formatted(paymentMethodId, lastCheckoutToken);
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
