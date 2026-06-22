package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.PaymentRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the public payment processing guard.
 *
 * Uses far-future date windows (900+, 910+, 920+ days) to avoid conflicts with
 * other tests sharing the same H2 in-memory database. TransferBookingControllerTest
 * occupies windows up to 870 days out; all windows here start at 900+.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockPaymentFlowTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private String lastCheckoutToken;

    // ── 1. Public process without a real Stripe intent is rejected ────────────

    @Test
    void processPayment_withoutPaymentIntent_isRejectedAndBookingStaysPending() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(900, 902), daysFromNow(900), daysFromNow(902));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid booking state"))
                .andExpect(jsonPath("$.message")
                        .value("Payment can only be completed after a real Stripe PaymentIntent has been created"));

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
        assertThat(paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(
                bookingRepository.findById(bookingId).orElseThrow()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
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

    // ── 3. Fake provider is not exposed through public checkout ──────────────

    @Test
    void publicPaymentIntentWithFakeProvider_failsClosedAndProcessIsRejected() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(910, 912), daysFromNow(910), daysFromNow(912));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intentRequestBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Payment provider not configured"))
                .andExpect(jsonPath("$.message").value("Payment processing is not available in this environment."));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Payment can only be completed after a real Stripe PaymentIntent has been created"));
    }

    // ── 4. Fake provider failure method cannot drive public booking state ─────

    @Test
    void processPayment_forceFailMethod_isRejectedAndBookingStaysPending() throws Exception {
        long bookingId = createBooking(anyAvailableCarId(920, 922), daysFromNow(920), daysFromNow(922));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_fail")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid booking state"));

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
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
                {"paymentMethodId": "%s", "checkoutSessionToken": "%s"}
                """.formatted(paymentMethodId, lastCheckoutToken);
    }

    private String intentRequestBody() {
        return """
                {"checkoutSessionToken": "%s"}
                """.formatted(lastCheckoutToken);
    }
}
