package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.email.FakeEmailService;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestCheckoutPaymentSecurityTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private FakeEmailService fakeEmailService;

    @BeforeEach
    void clearEmails() {
        fakeEmailService.clearSentEmails();
    }

    @Test
    void guestBookingCreationRemainsPendingAndUnpaid() throws Exception {
        CreatedBooking created = createGuestBooking(2400, 2402);

        assertThat(created.checkoutToken()).isNotBlank();
        var booking = bookingRepository.findById(created.id()).orElseThrow();
        var payment = paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(fakeEmailService.getSentConfirmationEmails()).isEmpty();
    }

    @Test
    void guestProcessPaymentWithoutStripeIntentCannotConfirmOrPay() throws Exception {
        CreatedBooking created = createGuestBooking(2410, 2412);

        mockMvc.perform(post("/api/bookings/{id}/payments/process", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid", created.checkoutToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid booking state"))
                .andExpect(jsonPath("$.message").value("Payment can only be completed after a real Stripe PaymentIntent has been created"));

        assertStillPendingAndUnpaid(created.id());
        assertThat(fakeEmailService.getSentConfirmationEmails()).isEmpty();
    }

    @Test
    void guestFakeProviderIntentFailsClosedAndCannotBeUsedAsStripeSuccess() throws Exception {
        CreatedBooking created = createGuestBooking(2420, 2422);

        mockMvc.perform(post("/api/bookings/{id}/payments/intent", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"checkoutSessionToken\":\"" + created.checkoutToken() + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Payment provider not configured"))
                .andExpect(jsonPath("$.message").value("Payment processing is not available in this environment."));

        mockMvc.perform(post("/api/bookings/{id}/payments/process", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid", created.checkoutToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid booking state"))
                .andExpect(jsonPath("$.message").value("Payment can only be completed after a real Stripe PaymentIntent has been created"));

        assertStillPendingAndUnpaid(created.id());
        assertThat(fakeEmailService.getSentConfirmationEmails()).isEmpty();
    }

    @Test
    void guestBookingLookupDoesNotExposeConfirmedOrPaidWithoutVerifiedStripePayment() throws Exception {
        CreatedBooking created = createGuestBooking(2430, 2432);

        mockMvc.perform(get("/api/bookings/{id}", created.id())
                        .header("X-Checkout-Session-Token", created.checkoutToken()))
                .andExpect(status().isUnauthorized());

        assertStillPendingAndUnpaid(created.id());
    }

    @Test
    void legacyPaymentsCheckoutCannotConfirmGuestBooking() throws Exception {
        CreatedBooking created = createGuestBooking(2440, 2442);

        mockMvc.perform(post("/api/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + created.id() + "}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("Legacy checkout is disabled. Use Stripe PaymentIntent verification."));

        assertStillPendingAndUnpaid(created.id());
        assertThat(fakeEmailService.getSentConfirmationEmails()).isEmpty();
    }

    private void assertStillPendingAndUnpaid(long bookingId) {
        var booking = bookingRepository.findById(bookingId).orElseThrow();
        var payment = paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaidAt()).isNull();
    }

    private CreatedBooking createGuestBooking(int pickupOffset, int dropoffOffset) throws Exception {
        long carId = anyAvailableCarId(pickupOffset, dropoffOffset);
        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(pickupOffset), daysFromNow(dropoffOffset))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andReturn();

        String token = result.getResponse().getHeader("X-Checkout-Session-Token");
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        return new CreatedBooking(id, token);
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

    private String bookingBody(long carId, String pickup, String dropoff) {
        return """
                {
                  "carId": %d,
                  "customerName": "Guest Security",
                  "customerEmail": "guest-security@example.com",
                  "customerPhone": "+34600000022",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport T1",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, pickup, dropoff);
    }

    private String daysFromNow(int days) {
        return LocalDateTime.now()
                .plusDays(days)
                .withHour(10).withMinute(0).withSecond(0).withNano(0)
                .format(FMT);
    }

    private String payBody(String paymentMethodId, String checkoutToken) {
        return """
                {"paymentMethodId":"%s","checkoutSessionToken":"%s"}
                """.formatted(paymentMethodId, checkoutToken);
    }

    private record CreatedBooking(long id, String checkoutToken) {
    }
}
