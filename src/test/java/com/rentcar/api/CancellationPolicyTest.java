package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.repository.BookingRepository;
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
 * Integration tests for GET /api/bookings/manage/cancellation-policy.
 *
 * Policy cases covered:
 *  1. CANCELLED booking             → cancellable=false
 *  2. Pickup in the past            → cancellable=false
 *  3. CONFIRMED + pickup > 24h away → cancellable=true, full refund
 *  4. CONFIRMED + pickup ≤ 24h away → cancellable=true, no refund
 *  5. PENDING booking               → cancellable=true, no charge
 *  6. FAILED booking                → cancellable=true, no charge
 *  7. Unknown reference             → 404
 *
 * Uses far-future date windows starting at 1100 days to avoid conflicts with
 * all existing tests (ManageBookingTest uses up to 994, MockPaymentFlowTest up to 922,
 * TransferBookingControllerTest up to 870, BookingPricingIntegrationTest up to 231).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CancellationPolicyTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    // ── 1. Already-cancelled booking → not cancellable ────────────────────────

    @Test
    void policy_cancelledBooking_notCancellable() throws Exception {
        BookingInfo b = createPendingBooking(1100, 1102, "Carl Cancel", "carl.cancel@test.com");

        // Cancel via existing endpoint (admin-only)
        mockMvc.perform(post("/api/bookings/" + b.id() + "/cancel")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/bookings/manage/cancellation-policy")
                        .param("bookingReference", b.reference())
                        .param("lastName", "Cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.reason").value("Booking is already cancelled."))
                .andExpect(jsonPath("$.refundEligible").value(false))
                .andExpect(jsonPath("$.refundAmount").value(0.00))
                .andExpect(jsonPath("$.cancellationFee").value(0.00));
    }

    // ── 2. Pickup in the past → not cancellable ───────────────────────────────

    @Test
    void policy_pastPickup_notCancellable() throws Exception {
        BookingInfo b = createPendingBooking(1110, 1112, "Past Pickup", "past.pickup@test.com");

        // Directly set pickup to yesterday via repository — simulates a booking
        // that was created for a future date but time has now passed.
        Booking booking = bookingRepository.findById(b.id()).orElseThrow();
        LocalDateTime originalPickup = booking.getPickupDateTime();
        booking.setPickupDateTime(LocalDateTime.now().minusDays(1));
        // Capture returned entity — its @Version field is incremented by the save.
        // Discarding the return value would leave `booking` with a stale version,
        // causing StaleObjectStateException in the finally restore below.
        Booking saved = bookingRepository.saveAndFlush(booking);

        try {
            mockMvc.perform(get("/api/bookings/manage/cancellation-policy")
                            .param("bookingReference", b.reference())
                            .param("lastName", "Pickup"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancellable").value(false))
                    .andExpect(jsonPath("$.reason").value("Your pickup date has passed."))
                    .andExpect(jsonPath("$.refundEligible").value(false))
                    .andExpect(jsonPath("$.refundAmount").value(0.00));
        } finally {
            // Restore original date so this booking does not block other tests' car-search windows.
            saved.setPickupDateTime(originalPickup);
            bookingRepository.saveAndFlush(saved);
        }
    }

    // ── 3. CONFIRMED + pickup > 24h away → full refund ────────────────────────

    @Test
    void policy_confirmedMoreThan24h_fullRefundEligible() throws Exception {
        BookingInfo b = createConfirmedBooking(1120, 1122, "Full Refund", "full.refund@test.com");

        MvcResult result = mockMvc.perform(get("/api/bookings/manage/cancellation-policy")
                        .param("bookingReference", b.reference())
                        .param("lastName", "Refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.refundEligible").value(true))
                .andExpect(jsonPath("$.cancellationFee").value(0.00))
                .andExpect(jsonPath("$.policyMessage").value("Full refund will be applied."))
                .andReturn();

        // refundAmount must equal the booking totalPrice (positive number)
        double refundAmount = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.refundAmount")).doubleValue();
        assertThat(refundAmount).isGreaterThan(0.0);
    }

    // ── 4. CONFIRMED + pickup ≤ 24h away → no refund ─────────────────────────

    @Test
    void policy_confirmedWithin24h_noRefund() throws Exception {
        // Use a pickup 2h from now — satisfies the ">1h" API guard and is within the 24h window.
        // The booking occupies the car for only a few hours so it doesn't block other test windows.
        String pickup  = hoursFromNow(2);
        String dropoff = hoursFromNow(4);
        BookingInfo b  = createConfirmedBookingWithDates(pickup, dropoff, "No Refund", "no.refund@test.com");

        mockMvc.perform(get("/api/bookings/manage/cancellation-policy")
                        .param("bookingReference", b.reference())
                        .param("lastName", "Refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.refundEligible").value(false))
                .andExpect(jsonPath("$.refundAmount").value(0.00))
                .andExpect(jsonPath("$.cancellationFee").value(0.00))
                .andExpect(jsonPath("$.policyMessage")
                        .value("Cancellation within 24 hours of pickup — no refund applies."));
    }

    // ── 5. PENDING booking → cancellable, no charge ───────────────────────────

    @Test
    void policy_pendingBooking_cancellableNoCharge() throws Exception {
        BookingInfo b = createPendingBooking(1140, 1142, "Pending User", "pending.user@test.com");

        mockMvc.perform(get("/api/bookings/manage/cancellation-policy")
                        .param("bookingReference", b.reference())
                        .param("lastName", "User"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.refundEligible").value(false))
                .andExpect(jsonPath("$.refundAmount").value(0.00))
                .andExpect(jsonPath("$.cancellationFee").value(0.00))
                .andExpect(jsonPath("$.policyMessage")
                        .value("Your booking has not been paid — no charge applies."));
    }

    // ── 6. FAILED booking → cancellable, no charge ───────────────────────────

    @Test
    void policy_failedBooking_cancellableNoCharge() throws Exception {
        BookingInfo b = createPendingBooking(1150, 1152, "Failed Pay", "failed.pay@test.com");

        // Trigger payment failure → booking moves to FAILED
        mockMvc.perform(post("/api/bookings/" + b.id() + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"pm_fail\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(get("/api/bookings/manage/cancellation-policy")
                        .param("bookingReference", b.reference())
                        .param("lastName", "Pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.refundEligible").value(false))
                .andExpect(jsonPath("$.refundAmount").value(0.00))
                .andExpect(jsonPath("$.policyMessage")
                        .value("Your booking has not been paid — no charge applies."));
    }

    // ── 7. Unknown reference → 404 ───────────────────────────────────────────

    @Test
    void policy_unknownReference_returns404() throws Exception {
        mockMvc.perform(get("/api/bookings/manage/cancellation-policy")
                        .param("bookingReference", "RC-000000-ZZZZ")
                        .param("lastName", "Nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.message").value(
                        "We couldn't find a booking with these details. Please check your reference and last name."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a booking and processes payment with pm_test_valid → CONFIRMED. */
    private BookingInfo createConfirmedBooking(
            int pickupOffset, int dropoffOffset, String name, String email) throws Exception {
        BookingInfo b = createPendingBooking(pickupOffset, dropoffOffset, name, email);
        mockMvc.perform(post("/api/bookings/" + b.id() + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"pm_test_valid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        return b;
    }

    /** Creates a CONFIRMED booking using explicit pickup/dropoff datetime strings. */
    private BookingInfo createConfirmedBookingWithDates(
            String pickup, String dropoff, String name, String email) throws Exception {
        BookingInfo b = createPendingBookingWithDates(pickup, dropoff, name, email);
        mockMvc.perform(post("/api/bookings/" + b.id() + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"pm_test_valid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        return b;
    }

    /** Creates a booking (no payment) → PENDING. */
    private BookingInfo createPendingBooking(
            int pickupOffset, int dropoffOffset, String name, String email) throws Exception {
        return createPendingBookingWithDates(
                daysFromNow(pickupOffset), daysFromNow(dropoffOffset), name, email);
    }

    private BookingInfo createPendingBookingWithDates(
            String pickup, String dropoff, String name, String email) throws Exception {
        long carId = anyAvailableCarIdForDates(pickup, dropoff);
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, pickup, dropoff, name, email)))
                .andExpect(status().isOk())
                .andReturn();
        long id  = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String ref = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        return new BookingInfo(id, ref);
    }

    private long anyAvailableCarId(int pickupOffset, int dropoffOffset) throws Exception {
        return anyAvailableCarIdForDates(daysFromNow(pickupOffset), daysFromNow(dropoffOffset));
    }

    private long anyAvailableCarIdForDates(String pickup, String dropoff) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", pickup)
                        .param("dropoffDateTime", dropoff))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).as("No cars available for [%s, %s]", pickup, dropoff).isNotEmpty();
        return ids.get(0).longValue();
    }

    private String daysFromNow(int days) {
        return LocalDateTime.now()
                .plusDays(days)
                .withHour(10).withMinute(0).withSecond(0).withNano(0)
                .format(FMT);
    }

    private String hoursFromNow(int hours) {
        return LocalDateTime.now()
                .plusHours(hours)
                .withSecond(0).withNano(0)
                .format(FMT);
    }

    private String bookingBody(long carId, String pickup, String dropoff, String name, String email) {
        return """
                {
                  "carId": %d,
                  "customerName": "%s",
                  "customerEmail": "%s",
                  "customerPhone": "+34600000099",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, name, email, pickup, dropoff);
    }

    private record BookingInfo(long id, String reference) {}
}
