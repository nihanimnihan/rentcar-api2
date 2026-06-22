package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.PaymentRepository;
import com.rentcar.api.service.ManageBookingTokenService;
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
 * Integration tests for POST /api/bookings/manage/cancel.

 * Covered scenarios:
 *  1. Cancellable PENDING booking cancelled by reference + lastName → CANCELLED
 *  2. Cancellable CONFIRMED fake-paid booking → CANCELLED, payment REFUND_PENDING
 *  3. Wrong lastName cannot cancel → 404
 *  4. Already-cancelled booking cannot be cancelled again → 409
 *  5. Booking with past pickup cannot be cancelled → 409

 * Uses date windows starting at 1200 days to avoid conflicts with all existing
 * tests (CancellationPolicyTest occupies 1100–1152, ManageBookingTest 950–1006,
 * MockPaymentFlowTest 900–922, TransferBookingControllerTest 800–870).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManageCancelTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";

    @Autowired private MockMvc mockMvc;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ManageBookingTokenService manageBookingTokenService;

    // ── 1. Cancel PENDING booking by reference + lastName ────────────────────

    @Test
    void cancel_pendingBooking_returnsCANCELLED() throws Exception {
        BookingInfo b = createPendingBooking(1200, 1202, "Alice Cancel", "alice.cancel@test.com");

        mockMvc.perform(post("/api/bookings/manage/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody(b.reference(), "Cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(b.reference()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ── 2. Cancel CONFIRMED booking without Stripe reference → REFUND_PENDING

    @Test
    void cancel_confirmedRefundEligibleBooking_refundPendingWithoutStripeReference() throws Exception {
        BookingInfo b = createConfirmedBooking(1210, 1212, "Bob Refund", "bob.refund@test.com");

        MvcResult result = mockMvc.perform(post("/api/bookings/manage/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody(b.reference(), "Refund")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(b.reference()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andReturn();

        // No real Stripe PaymentIntent/charge exists, so no fake refund is created.
        String paymentStatus = JsonPath.read(
                result.getResponse().getContentAsString(), "$.paymentStatus");
        assertThat(paymentStatus).isEqualTo("REFUND_PENDING");
    }

    // ── 3. Wrong lastName → 404 (same safe message as lookup) ────────────────

    @Test
    void cancel_wrongLastName_returns404() throws Exception {
        BookingInfo b = createPendingBooking(1220, 1222, "Charlie Wrong", "charlie.wrong@test.com");

        mockMvc.perform(post("/api/bookings/manage/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody(b.reference(), "Incorrect")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    // ── 4. Already-cancelled booking → 409 ───────────────────────────────────

    @Test
    void cancel_alreadyCancelledBooking_returns409() throws Exception {
        BookingInfo b = createPendingBooking(1230, 1232, "Dana Double", "dana.double@test.com");

        // First cancel succeeds.
        mockMvc.perform(post("/api/bookings/manage/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody(b.reference(), "Double")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Second cancel attempt must be rejected.
        mockMvc.perform(post("/api/bookings/manage/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody(b.reference(), "Double")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Booking cannot be cancelled"))
                .andExpect(jsonPath("$.message").value("This booking has already been cancelled."));
    }

    // ── 5. Pickup in the past → 409 ───────────────────────────────────────────

    @Test
    void cancel_pastPickup_returns409() throws Exception {
        BookingInfo b = createPendingBooking(1240, 1242, "Eve Past", "eve.past@test.com");

        // Move pickup to yesterday via repository (simulates elapsed time).
        Booking booking = bookingRepository.findById(b.id()).orElseThrow();
        LocalDateTime originalPickup = booking.getPickupDateTime();
        booking.setPickupDateTime(LocalDateTime.now().minusDays(1));
        Booking saved = bookingRepository.saveAndFlush(booking);

        try {
            mockMvc.perform(post("/api/bookings/manage/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cancelBody(b.reference(), "Past")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("Booking cannot be cancelled"))
                    .andExpect(jsonPath("$.message")
                            .value("The booking can no longer be modified after the pickup date."));
        } finally {
            saved.setPickupDateTime(originalPickup);
            bookingRepository.saveAndFlush(saved);
        }
    }

    @Test
    void cancel_validManageToken_returnsCancelled() throws Exception {
        BookingInfo b = createConfirmedBooking(1250, 1252, "Frank Token", "frank.token@test.com");
        Booking booking = bookingRepository.findById(b.id()).orElseThrow();
        String token = manageBookingTokenService.issueToken(booking);

        mockMvc.perform(post("/api/bookings/manage/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelTokenBody(token, "Token cancellation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(b.reference()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Token cancellation"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BookingInfo createConfirmedBooking(int pickupOffset, int dropoffOffset,
                                               String name, String email) throws Exception {
        BookingInfo b = createPendingBooking(pickupOffset, dropoffOffset, name, email);
        TestPaymentFixtures.markConfirmedPaidWithoutStripeReference(
                bookingRepository,
                paymentRepository,
                b.id());
        return b;
    }

    private BookingInfo createPendingBooking(int pickupOffset, int dropoffOffset,
                                             String name, String email) throws Exception {
        long carId = anyAvailableCarId(pickupOffset, dropoffOffset);
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(pickupOffset), daysFromNow(dropoffOffset), name, email)))
                .andExpect(status().isOk())
                .andReturn();
        long id  = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String ref = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        String checkoutToken = created.getResponse().getHeader("X-Checkout-Session-Token");
        return new BookingInfo(id, ref, checkoutToken);
    }

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

    private String daysFromNow(int days) {
        return LocalDateTime.now()
                .plusDays(days)
                .withHour(10).withMinute(0).withSecond(0).withNano(0)
                .format(FMT);
    }

    private String cancelBody(String bookingReference, String lastName) {
        return """
                {"bookingReference": "%s", "lastName": "%s"}
                """.formatted(bookingReference, lastName);
    }

    private String cancelTokenBody(String token, String cancellationReason) {
        return """
                {"token": "%s", "cancellationReason": "%s"}
                """.formatted(token, cancellationReason);
    }

    private String bookingBody(long carId, String pickup, String dropoff,
                               String name, String email) {
        return """
                {
                  "carId": %d,
                  "customerName": "%s",
                  "customerEmail": "%s",
                  "customerPhone": "+34600000088",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, name, email, pickup, dropoff);
    }

    private record BookingInfo(long id, String reference, String checkoutToken) {}
}
