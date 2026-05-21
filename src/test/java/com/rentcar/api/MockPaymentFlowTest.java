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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the MVP mock payment happy path.
 *
 * The dev-profile FakePaymentProvider returns PaymentResult(true, ...) by default,
 * and PaymentResult(false, ...) when paymentMethodId == FakePaymentProvider.FORCE_FAIL_METHOD_ID.
 * This allows testing the full success/failure lifecycle without mocking the Spring context.
 *
 * Endpoint under test: POST /api/bookings/{id}/payments/process (public, no auth required)
 *
 * Uses far-future date windows (900+, 910+, 920+ days) to avoid conflicts with
 * other tests sharing the same H2 in-memory database. TransferBookingControllerTest
 * occupies windows up to 870 days out; all windows here start at 900+.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MockPaymentFlowTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";

    @Autowired
    private MockMvc mockMvc;

    // ── 1. Happy path ─────────────────────────────────────────────────────────

    @Test
    void processPayment_happyPath_bookingConfirmedAndPaymentPaid() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(900, 902), daysFromNow(900), daysFromNow(902));

        // Process payment — FakePaymentProvider returns success
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Verify the payment record is PAID via admin endpoint
        MvcResult paymentsResult = mockMvc.perform(get("/api/payments")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> bookingIds = JsonPath.read(
                paymentsResult.getResponse().getContentAsString(), "$[*].bookingId");
        List<String> statuses = JsonPath.read(
                paymentsResult.getResponse().getContentAsString(), "$[*].status");

        int idx = bookingIds.indexOf((int) bookingId);
        assertThat(idx).as("No payment record found for bookingId=%d", bookingId).isNotNegative();
        assertThat(statuses.get(idx)).isEqualTo("PAID");
    }

    // ── 2. Unknown booking id ─────────────────────────────────────────────────

    @Test
    void processPayment_unknownBookingId_returns404() throws Exception {
        mockMvc.perform(post("/api/bookings/999999/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_any")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    // ── 3. Double payment is rejected ─────────────────────────────────────────

    @Test
    void processPayment_alreadyPaidBooking_returns409() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(910, 912), daysFromNow(910), daysFromNow(912));

        // First payment succeeds — booking moves to CONFIRMED
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Second payment attempt on an already-CONFIRMED booking must be rejected
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid booking state"));
    }

    // ── 4. Provider failure — controlled response, no NPE/500 ─────────────────

    @Test
    void processPayment_providerFailure_bookingFailedNoNpe() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(920, 922), daysFromNow(920), daysFromNow(922));

        // FakePaymentProvider returns PaymentResult(false, ...) for FORCE_FAIL_METHOD_ID
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(FakePaymentProvider.FORCE_FAIL_METHOD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                // Provider failure is absorbed: booking status becomes FAILED, no crash
                .andExpect(jsonPath("$.status").value("FAILED"));
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
                  "customerName": "Payment Flow Test",
                  "customerEmail": "payment-flow@example.com",
                  "customerPhone": "+34600000077",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, pickup, dropoff);
    }

    private String payBody(String paymentMethodId) {
        return """
                {"paymentMethodId": "%s"}
                """.formatted(paymentMethodId);
    }
}
