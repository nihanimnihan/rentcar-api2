package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.payment.model.PaymentIntentVerification;
import com.rentcar.api.email.CancellationEmailData;
import com.rentcar.api.email.ConfirmationEmailData;
import com.rentcar.api.email.EmailLocalizationService;
import com.rentcar.api.email.FakeEmailService;
import com.rentcar.api.email.RefundCompletedEmailData;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.PaymentRepository;
import com.rentcar.api.payment.provider.PaymentProvider;
import com.rentcar.api.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
@ActiveProfiles("test")
class BookingEmailNotificationTest {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired MockMvc mockMvc;
    @Autowired FakeEmailService fakeEmailService;
    @Autowired EmailLocalizationService emailLocalizationService;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentService paymentService;
    @MockitoBean
    PaymentProvider paymentProvider;

    @BeforeEach
    void clearEmails() {
        TestPaymentFixtures.configureStripeIntentProvider(paymentProvider);
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

        TestPaymentFixtures.confirmByVerifiedStripeWebhook(bookingRepository, paymentService, bookingId);

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
        TestPaymentFixtures.confirmByVerifiedStripeWebhook(bookingRepository, paymentService, bookingId);

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
        assertThat(email.language()).isEqualTo("en");
        assertTokenizedManageUrl(email.managementUrl());

        var rendered = emailLocalizationService.bookingConfirmation(email);
        assertThat(rendered.subject()).isEqualTo("Paradise Deluxe booking confirmed - " + bookingRef);
        assertThat(rendered.body())
                .contains("Your Paradise Deluxe booking is confirmed.")
                .contains("Thank you for choosing Paradise Deluxe.")
                .doesNotContain("RentCar booking")
                .doesNotContain("Thank you for choosing RentCar");
    }

    @Test
    void turkishBooking_sendsTurkishConfirmationEmail() throws Exception {
        long carId = anyAvailableCarId(1412, 1413);

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1412), daysFromNow(1413),
                                "Turkish Confirmation", "turkish.confirmation@example.com", "tr")))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String bookingRef = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        var persistedBooking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(persistedBooking.getLanguage()).isEqualTo("tr");
        assertThat(persistedBooking.getCustomer().getPreferredLanguage()).isEqualTo("tr");

        TestPaymentFixtures.confirmByVerifiedStripeWebhook(bookingRepository, paymentService, bookingId);

        List<ConfirmationEmailData> emails = fakeEmailService.getSentEmails();
        assertThat(emails).hasSize(1);

        ConfirmationEmailData email = emails.get(0);
        assertThat(email.language()).isEqualTo("tr");
        var rendered = emailLocalizationService.bookingConfirmation(email);
        assertThat(rendered.subject()).isEqualTo("Paradise Deluxe rezervasyonunuz onaylandı - " + bookingRef);
        assertThat(rendered.body())
                .contains("Paradise Deluxe rezervasyonunuz onaylandı.")
                .contains("Rezervasyon referansı: " + bookingRef)
                .contains("Paradise Deluxe’i tercih ettiğiniz için teşekkür ederiz.")
                .doesNotContain("RentCar");
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
        TestPaymentFixtures.failByVerifiedStripeWebhook(bookingRepository, paymentService, bookingId);

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
                                "Cancel Reason", "cancel.reason@example.com", "tr")))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String bookingRef = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        TestPaymentFixtures.markConfirmedPaidWithoutStripeReference(
                bookingRepository,
                paymentRepository,
                bookingId);

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
        assertThat(email.refundStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(email.language()).isEqualTo("tr");
        assertTokenizedManageUrl(email.managementUrl());

        var rendered = emailLocalizationService.bookingCancellation(email);
        assertThat(rendered.subject()).isEqualTo("Paradise Deluxe rezervasyonunuz iptal edildi - " + bookingRef);
        assertThat(rendered.body())
                .contains("Paradise Deluxe rezervasyonunuz iptal edildi.")
                .contains("İade durumu: İade başlatıldı")
                .contains("İade uygulanıyorsa, tutarın banka hesabınızda görünmesi birkaç iş günü sürebilir.")
                .contains("Paradise Deluxe’i tercih ettiğiniz için teşekkür ederiz.")
                .doesNotContain("RentCar");
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
        var payment = paymentService.findLatestPayment(booking).orElseThrow();
        String intentId = payment.getStripePaymentIntentId();
        assertThat(intentId).isNotBlank();

        paymentService.applyStripePaymentIntentStatus(intent(payment, "succeeded"), "ch_test_123");
        paymentService.applyStripePaymentIntentStatus(intent(payment, "succeeded"), "ch_test_123");

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
                                "Refund Webhook", "refund.webhook@example.com", "tr")))
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

        TestPaymentFixtures.confirmByVerifiedStripeWebhook(bookingRepository, paymentService, bookingId);

        paymentService.applyStripeRefundStatus(intentId, "re_test_123", "succeeded");
        paymentService.applyStripeRefundStatus(intentId, "re_test_123", "succeeded");

        List<RefundCompletedEmailData> emails = fakeEmailService.getSentRefundCompletedEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).bookingReference()).isEqualTo(bookingRef);
        assertThat(emails.get(0).customerEmail()).isEqualTo("refund.webhook@example.com");
        assertThat(emails.get(0).refundReference()).isEqualTo("re_test_123");
        assertThat(emails.get(0).language()).isEqualTo("tr");
        assertTokenizedManageUrl(emails.get(0).managementUrl());

        var rendered = emailLocalizationService.refundCompleted(emails.get(0));
        assertThat(rendered.subject()).isEqualTo("Paradise Deluxe iadeniz tamamlandı - " + bookingRef);
        assertThat(rendered.body())
                .contains("Paradise Deluxe rezervasyonunuz için iade tamamlandı.")
                .contains("İade tarafımızda tamamlandı, ancak tutarın banka hesabınızda görünmesi birkaç iş günü sürebilir.")
                .contains("Paradise Deluxe’i tercih ettiğiniz için teşekkür ederiz.")
                .doesNotContain("RentCar");
    }

    @Test
    void customerLifecycleEmailsUseSharedLocalizedParadiseDeluxeBranding() {
        ConfirmationEmailData confirmation = new ConfirmationEmailData(
                "RC-TEST",
                "brand@example.com",
                "Brand Test",
                LocalDateTime.now().plusDays(1),
                "Airport T1",
                LocalDateTime.now().plusDays(2),
                "Airport T1",
                "Mercedes Vito",
                "Full Protection",
                java.math.BigDecimal.valueOf(42),
                java.math.BigDecimal.valueOf(300),
                java.math.BigDecimal.valueOf(120),
                "http://localhost:8091/manage-booking.html?token=test",
                "es");
        CancellationEmailData cancellation = new CancellationEmailData(
                "RC-TEST",
                "brand@example.com",
                "Brand Test",
                "Cambio de planes",
                PaymentStatus.REFUND_PENDING,
                "http://localhost:8091/manage-booking.html?token=test",
                "es");
        RefundCompletedEmailData refund = new RefundCompletedEmailData(
                "RC-TEST",
                "brand@example.com",
                "Brand Test",
                "re_test",
                "http://localhost:8091/manage-booking.html?token=test",
                "es");

        assertThat(emailLocalizationService.bookingConfirmation(confirmation).subject())
                .isEqualTo("Reserva Paradise Deluxe confirmada - RC-TEST");
        assertThat(emailLocalizationService.bookingCancellation(cancellation).subject())
                .isEqualTo("Reserva Paradise Deluxe cancelada - RC-TEST");
        assertThat(emailLocalizationService.refundCompleted(refund).subject())
                .isEqualTo("Reembolso Paradise Deluxe completado - RC-TEST");

        assertThat(emailLocalizationService.bookingConfirmation(confirmation).body()).contains("Gracias por elegir Paradise Deluxe").doesNotContain("RentCar");
        assertThat(emailLocalizationService.bookingCancellation(cancellation).body()).contains("Gracias por elegir Paradise Deluxe").doesNotContain("RentCar");
        assertThat(emailLocalizationService.refundCompleted(refund).body()).contains("Gracias por elegir Paradise Deluxe").doesNotContain("RentCar");
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
        return bookingBody(carId, pickup, dropoff, name, email, null);
    }

    private String bookingBody(long carId, String pickup, String dropoff,
                               String name, String email, String language) {
        String languageLine = language == null
                ? ""
                : """
                  "language": "%s",
                """.formatted(language);
        return """
                {
                  "carId": %d,
                  "customerName": "%s",
                  "customerEmail": "%s",
                  "customerPhone": "+34600000077",
                %s
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport T1",
                  "dropoffLocation": "Airport T1",
                  "insurancePackageId": 1
                }
                """.formatted(carId, name, email, languageLine, pickup, dropoff);
    }

    private PaymentIntentVerification intent(com.rentcar.api.domain.payment.Payment payment, String status) {
        return new PaymentIntentVerification(
                payment.getStripePaymentIntentId(),
                status,
                payment.getAmount().movePointRight(2).longValueExact(),
                payment.getCurrencyCode().toLowerCase(java.util.Locale.ROOT),
                Map.of(
                        "bookingId", String.valueOf(payment.getBooking().getId()),
                        "paymentId", String.valueOf(payment.getId()),
                        "paymentReference", payment.getPaymentReference()
                )
        );
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
