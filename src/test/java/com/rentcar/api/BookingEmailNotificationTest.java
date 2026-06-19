package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.email.CancellationEmailData;
import com.rentcar.api.email.ConfirmationEmailData;
import com.rentcar.api.email.FakeEmailService;
import com.rentcar.api.email.RefundCompletedEmailData;
import com.rentcar.api.payment.provider.FakePaymentProvider;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.service.PaymentService;
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

/**
 * Integration tests verifying that the confirmation email is triggered correctly
 * after successful payment and not triggered on payment failure.
 *
 * Uses {@link FakeEmailService} (active on the dev profile) as the email backend —
 * emails are captured in-memory for assertion rather than delivered.
 *
 * Date window 1400+ to avoid conflicts with all prior test windows:
 *   BookingAuditMetadataTest: 1300+, ManageCancelTest: 1200–1242,
 *   CancellationPolicyTest: 1100–1152, ManageBookingTest: 950–994,
 *   MockPaymentFlowTest: 900–922, TransferBookingControllerTest: 800–870.
 */
@SpringBootTest(properties = "app.public-base-url=http://localhost:8091")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingEmailNotificationTest {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired MockMvc mockMvc;
    @Autowired FakeEmailService fakeEmailService;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentService paymentService;

    @BeforeEach
    void clearEmails() {
        fakeEmailService.clearSentEmails();
    }

    // ── 1. Successful payment triggers confirmation email ─────────────────────

    @Test
    void successfulPayment_triggersConfirmationEmail() throws Exception {
        long carId = anyAvailableCarId(1400, 1401);

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1400), daysFromNow(1401),
                                "Email Test User", "emailtest1@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String checkoutToken = created.getResponse().getHeader("X-Checkout-Session-Token");

        assertThat(fakeEmailService.getSentEmails()).as("No email before payment").isEmpty();

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid", checkoutToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(fakeEmailService.getSentEmails())
                .as("Exactly one confirmation email should be captured")
                .hasSize(1);
    }

    // ── 2. Email contains bookingReference ────────────────────────────────────

    @Test
    void confirmationEmail_containsBookingReference() throws Exception {
        long carId = anyAvailableCarId(1402, 1403);

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1402), daysFromNow(1403),
                                "Reference Check", "emailtest2@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String bookingRef = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        String checkoutToken = created.getResponse().getHeader("X-Checkout-Session-Token");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid", checkoutToken)))
                .andExpect(status().isOk());

        List<ConfirmationEmailData> emails = fakeEmailService.getSentEmails();
        assertThat(emails).hasSize(1);

        ConfirmationEmailData email = emails.get(0);
        assertThat(email.bookingReference()).isEqualTo(bookingRef);
        assertThat(email.customerEmail()).isEqualTo("emailtest2@example.com");
        assertThat(email.customerName()).isEqualTo("Reference Check");
        assertThat(email.pickupLocation()).isNotBlank();
        assertThat(email.dropoffLocation()).isNotBlank();
        assertThat(email.selectedService()).isNotBlank();
        assertThat(email.totalPrice()).isPositive();
        assertTokenizedManageUrl(email.managementUrl());
    }

    // ── 3. Payment failure → no email sent ────────────────────────────────────

    @Test
    void failedPayment_doesNotTriggerEmail() throws Exception {
        long carId = anyAvailableCarId(1404, 1405);

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1404), daysFromNow(1405),
                                "Fail Test User", "emailtest3@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String checkoutToken = created.getResponse().getHeader("X-Checkout-Session-Token");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(FakePaymentProvider.FORCE_FAIL_METHOD_ID, checkoutToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(fakeEmailService.getSentEmails())
                .as("No email should be sent after a failed payment")
                .isEmpty();
    }

    // ── 4. Customer cancellation email includes reason + refund status ──────

    @Test
    void customerCancellation_sendsCancellationEmailWithReasonAndRefundStatus() throws Exception {
        long carId = anyAvailableCarId(1406, 1407);

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1406), daysFromNow(1407),
                                "Cancel Reason", "cancel.reason@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String bookingRef = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        String checkoutToken = created.getResponse().getHeader("X-Checkout-Session-Token");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid", checkoutToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/bookings/manage/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody(bookingRef, "Reason", "Travel plans changed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Travel plans changed"));

        List<CancellationEmailData> emails = fakeEmailService.getSentCancellationEmails();
        assertThat(emails).hasSize(1);

        CancellationEmailData email = emails.get(0);
        assertThat(email.bookingReference()).isEqualTo(bookingRef);
        assertThat(email.customerEmail()).isEqualTo("cancel.reason@example.com");
        assertThat(email.cancellationReason()).isEqualTo("Travel plans changed");
        assertThat(email.refundStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(email.refundStatusLabel()).isEqualTo("Refund completed");
        assertThat(email.bankProcessingMessage()).contains("few business days");
        assertTokenizedManageUrl(email.managementUrl());
    }

    // ── 5. Stripe async payment success sends confirmation once ─────────────

    @Test
    void stripePaymentIntentSucceeded_sendsConfirmationEmailOnce() throws Exception {
        long carId = anyAvailableCarId(1408, 1409);

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1408), daysFromNow(1409),
                                "Stripe Success", "stripe.success@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String bookingRef = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        String checkoutToken = created.getResponse().getHeader("X-Checkout-Session-Token");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intentBody(checkoutToken)))
                .andExpect(status().isOk());

        var booking = bookingRepository.findById(bookingId).orElseThrow();
        String intentId = paymentService.findLatestPayment(booking)
                .orElseThrow()
                .getStripePaymentIntentId();
        assertThat(intentId).isNotBlank();

        paymentService.applyStripePaymentIntentStatus(intentId, "succeeded", "ch_test_123");
        paymentService.applyStripePaymentIntentStatus(intentId, "succeeded", "ch_test_123");

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(fakeEmailService.getSentConfirmationEmails()).hasSize(1);
        assertThat(fakeEmailService.getSentConfirmationEmails().get(0).bookingReference())
                .isEqualTo(bookingRef);
    }

    // ── 6. Stripe refund-completed state sends one customer email ────────────

    @Test
    void stripeRefundSucceeded_sendsRefundCompletedEmailOnce() throws Exception {
        long carId = anyAvailableCarId(1410, 1411);

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1410), daysFromNow(1411),
                                "Refund Webhook", "refund.webhook@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String bookingRef = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        String checkoutToken = created.getResponse().getHeader("X-Checkout-Session-Token");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intentBody(checkoutToken)))
                .andExpect(status().isOk());

        var booking = bookingRepository.findById(bookingId).orElseThrow();
        String intentId = paymentService.findLatestPayment(booking)
                .orElseThrow()
                .getStripePaymentIntentId();
        assertThat(intentId).isNotBlank();

        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_test_valid", checkoutToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        paymentService.applyStripeRefundStatus(intentId, "re_test_123", "succeeded");
        paymentService.applyStripeRefundStatus(intentId, "re_test_123", "succeeded");

        List<RefundCompletedEmailData> emails = fakeEmailService.getSentRefundCompletedEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).bookingReference()).isEqualTo(bookingRef);
        assertThat(emails.get(0).customerEmail()).isEqualTo("refund.webhook@example.com");
        assertThat(emails.get(0).refundReference()).isEqualTo("re_test_123");
        assertThat(emails.get(0).bankProcessingMessage()).contains("few business days");
        assertTokenizedManageUrl(emails.get(0).managementUrl());
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

    private String daysFromNow(int days) {
        return LocalDateTime.now()
                .plusDays(days)
                .withHour(10).withMinute(0).withSecond(0).withNano(0)
                .format(FMT);
    }

    private String bookingBody(long carId, String pickup, String dropoff,
                               String name, String email) {
        return """
                {
                  "carId": %d,
                  "customerName": "%s",
                  "customerEmail": "%s",
                  "customerPhone": "+34600000077",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport T1",
                  "dropoffLocation": "Airport T1"
                }
                """.formatted(carId, name, email, pickup, dropoff);
    }

    private String payBody(String paymentMethodId, String checkoutToken) {
        return """
                {"paymentMethodId":"%s","checkoutSessionToken":"%s"}
                """.formatted(paymentMethodId, checkoutToken);
    }

    private String intentBody(String checkoutToken) {
        return """
                {"checkoutSessionToken":"%s"}
                """.formatted(checkoutToken);
    }

    private String cancelBody(String bookingReference, String lastName, String cancellationReason) {
        return """
                {
                  "bookingReference": "%s",
                  "lastName": "%s",
                  "cancellationReason": "%s"
                }
                """.formatted(bookingReference, lastName, cancellationReason);
    }

    private void assertTokenizedManageUrl(String managementUrl) {
        assertThat(managementUrl)
                .startsWith("http://localhost:8091/manage-booking.html?token=")
                .doesNotContain("bookingReference=");
        assertThat(managementUrl.substring(managementUrl.indexOf("token=") + "token=".length()))
                .isNotBlank();
    }
}
