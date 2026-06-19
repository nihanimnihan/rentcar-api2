package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.email.ConfirmationEmailData;
import com.rentcar.api.email.FakeEmailService;
import com.rentcar.api.payment.provider.FakePaymentProvider;
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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingEmailNotificationTest {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired MockMvc mockMvc;
    @Autowired FakeEmailService fakeEmailService;

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
        assertThat(email.totalPrice()).isPositive();
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
}
