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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the payment entity and state lifecycle.
 *
 * <p>Verifies the multi-attempt payment history model:
 * <ul>
 *   <li>Failed attempts are preserved; retries create new records (no overwrite).</li>
 *   <li>{@code findLatestPayment} deterministically returns the newest record.</li>
 *   <li>Successful payment sets {@code paidAt} and {@code updatedAt}.</li>
 *   <li>Duplicate payment attempts are blocked at the service layer.</li>
 *   <li>Cancellation of a PAID booking marks payment REFUNDED via fake provider.</li>
 * </ul>
 *
 * <p>Uses {@code GET /api/bookings/{id}/payments} (admin endpoint) to inspect
 * full payment history per booking.
 *
 * <p>Date windows start at 1700+ days to avoid conflicts with other test classes.
 * PaymentIntentTest occupies up to 1652; next batch starts here at 1700.
 *
 * <p>Related coverage in other test classes (not duplicated here):
 * <ul>
 *   <li>{@code MockPaymentFlowTest#processPayment_happyPath_*} — happy path via /payments/process</li>
 *   <li>{@code MockPaymentFlowTest#processPayment_alreadyPaidBooking_returns409} — duplicate guard</li>
 *   <li>{@code ManageCancelTest#cancel_confirmedRefundEligibleBooking_paymentRefunded} — refund path</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PaymentLifecycleTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";

    @Autowired
    private MockMvc mockMvc;

    private String lastCheckoutToken = null;

    // ── 1. Latest payment returns the newest attempt ──────────────────────────
    //      After failure + successful retry, history has 2 records.
    //      The newest (PAID) is returned by findLatestPayment.

    @Test
    void latestPayment_afterMultipleAttempts_returnsNewestRecord() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1700, 1702), daysFromNow(1700), daysFromNow(1702));

        // First attempt: forced failure — creates FAILED payment record
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(FakePaymentProvider.FORCE_FAIL_METHOD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // Retry: successful payment — creates new PAID record, old FAILED record preserved
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_valid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Payment history: 2 records, newest first
        MvcResult historyResult = mockMvc.perform(get("/api/bookings/" + bookingId + "/payments")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();

        List<String> statuses = JsonPath.read(historyResult.getResponse().getContentAsString(), "$[*].status");
        assertThat(statuses).as("payment history should have 2 records").hasSize(2);
        assertThat(statuses.get(0)).as("newest record should be PAID").isEqualTo("PAID");
        assertThat(statuses.get(1)).as("older record should be FAILED").isEqualTo("FAILED");

        // Booking-level latest payment (used by enrichWithPayment) is the newest = PAID
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    // ── 2. Failed attempt followed by retry creates a new pending payment ─────
    //      After failure, an intent call resets booking to PENDING and creates
    //      a fresh payment record. History grows from 1 to 2 records.

    @Test
    void failedAttempt_followedByRetry_createsFreshPendingPayment() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1710, 1712), daysFromNow(1710), daysFromNow(1712));

        // Force failure
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(FakePaymentProvider.FORCE_FAIL_METHOD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // Before retry: 1 payment record, FAILED
        MvcResult beforeHistory = mockMvc.perform(get("/api/bookings/" + bookingId + "/payments")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> beforeStatuses = JsonPath.read(beforeHistory.getResponse().getContentAsString(), "$[*].status");
        assertThat(beforeStatuses).hasSize(1);
        assertThat(beforeStatuses.get(0)).isEqualTo("FAILED");

        // Request intent on FAILED booking — creates fresh PENDING payment, resets booking to PENDING
        String freshPayRef = JsonPath.read(
                mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(intentRequestBody()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.paymentReference").value(matchesPattern("PAY-[0-9A-F]{8}")))
                        .andReturn().getResponse().getContentAsString(),
                "$.paymentReference");

        // After intent: 2 payment records — new PENDING on top, old FAILED preserved
        MvcResult afterHistory = mockMvc.perform(get("/api/bookings/" + bookingId + "/payments")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> afterStatuses = JsonPath.read(afterHistory.getResponse().getContentAsString(), "$[*].status");
        List<String> afterRefs = JsonPath.read(afterHistory.getResponse().getContentAsString(), "$[*].paymentReference");

        assertThat(afterStatuses).hasSize(2);
        assertThat(afterStatuses.get(0)).as("newest record should be PENDING").isEqualTo("PENDING");
        assertThat(afterStatuses.get(1)).as("preserved FAILED record").isEqualTo("FAILED");
        assertThat(afterRefs.get(0)).as("intent returned the new record's reference").isEqualTo(freshPayRef);
    }

    // ── 3. Successful payment marks booking CONFIRMED and payment PAID ─────────
    //      Verifies paidAt and updatedAt are set on the payment record.

    @Test
    void successfulPayment_marksBookingConfirmed_paymentPaidWithTimestamps() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1720, 1722), daysFromNow(1720), daysFromNow(1722));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_valid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        MvcResult historyResult = mockMvc.perform(get("/api/bookings/" + bookingId + "/payments")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();

        List<String> statuses = JsonPath.read(historyResult.getResponse().getContentAsString(), "$[*].status");
        assertThat(statuses).hasSize(1);
        assertThat(statuses.get(0)).isEqualTo("PAID");

        // paidAt and updatedAt must be present on a PAID record
        String body = historyResult.getResponse().getContentAsString();
        String paidAt = JsonPath.read(body, "$[0].paidAt");
        String updatedAt = JsonPath.read(body, "$[0].updatedAt");
        String createdAt = JsonPath.read(body, "$[0].createdAt");
        assertThat(paidAt).as("paidAt should be set after payment").isNotNull();
        assertThat(updatedAt).as("updatedAt should be set after status transition").isNotNull();
        assertThat(createdAt).as("createdAt should be set on persist").isNotNull();
    }

    // ── 4. Duplicate payment attempt is blocked — no extra payment record ─────
    //      After a CONFIRMED booking, a second /payments/process call returns 409
    //      and does NOT create a second payment record.

    @Test
    void duplicatePaymentAttempt_isRejected_noExtraPaymentRecord() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1730, 1732), daysFromNow(1730), daysFromNow(1732));

        // First payment succeeds
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_valid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Second attempt is rejected
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_valid")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid booking state"));

        // Payment history still has exactly 1 record — the rejected attempt created no record
        MvcResult historyResult = mockMvc.perform(get("/api/bookings/" + bookingId + "/payments")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> statuses = JsonPath.read(historyResult.getResponse().getContentAsString(), "$[*].status");
        assertThat(statuses).as("only one payment record should exist").hasSize(1);
        assertThat(statuses.get(0)).isEqualTo("PAID");
    }

    // ── 5. Cancellation of paid booking marks payment REFUNDED ────────────────
    //      The PAID payment record transitions to REFUNDED; paidAt is preserved
    //      and updatedAt advances to the refund time.

    @Test
    void cancellationOfPaidBooking_marksPaymentRefunded_withFakeProvider() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(1740, 1742), daysFromNow(1740), daysFromNow(1742));

        // Pay the booking
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_valid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Admin cancel (pickup is far future → refund eligible)
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Payment history: still 1 record, now REFUNDED
        MvcResult historyResult = mockMvc.perform(get("/api/bookings/" + bookingId + "/payments")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();

        String body = historyResult.getResponse().getContentAsString();
        List<String> statuses = JsonPath.read(body, "$[*].status");
        assertThat(statuses).hasSize(1);
        assertThat(statuses.get(0)).as("PAID payment should be REFUNDED after cancellation").isEqualTo("REFUNDED");

        // paidAt preserved; updatedAt advanced by the refund state transition
        String paidAt = JsonPath.read(body, "$[0].paidAt");
        String updatedAt = JsonPath.read(body, "$[0].updatedAt");
        assertThat(paidAt).as("paidAt should still be present after refund").isNotNull();
        assertThat(updatedAt).as("updatedAt should be set by @PreUpdate on refund").isNotNull();
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
        lastCheckoutToken = result.getResponse().getHeader("X-Checkout-Session-Token");
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String intentRequestBody() {
        if (lastCheckoutToken != null) {
            return String.format("{\"checkoutSessionToken\": \"%s\"}", lastCheckoutToken);
        }
        return "{}";
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
                  "customerName": "Lifecycle Test User",
                  "customerEmail": "lifecycle-test@example.com",
                  "customerPhone": "+34600000088",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport T1",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, pickup, dropoff);
    }

    private String payBody(String paymentMethodId) {
        return """
                {"paymentMethodId": "%s", "checkoutSessionToken": "%s"}
                """.formatted(paymentMethodId, lastCheckoutToken);
    }
}
