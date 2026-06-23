package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.service.ManageBookingTokenService;
import com.rentcar.api.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /api/bookings/manage.
 *
 * Scenarios:
 *  1. Valid reference + correct lastName → 200 with booking details
 *  2. Valid reference + wrong lastName   → 404
 *  3. Unknown reference                  → 404
 *  4. Case-insensitive lastName match    → 200
 *
 * Uses far-future date windows starting at 950 days to avoid H2 state conflicts
 * with other tests (MockPaymentFlowTest occupies 900–922, TransferBookingControllerTest 800–870).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManageBookingTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ManageBookingTokenService manageBookingTokenService;

    @Autowired
    private PaymentService paymentService;

    // ── 1. Valid reference + correct lastName → 200 ────────────────────────────

    @Test
    void manage_validReferenceAndLastName_returnsBooking() throws Exception {
        String ref = createConfirmedBookingAndGetReference(950, 952, "Alice Manage");

        mockMvc.perform(get("/api/bookings/manage")
                        .param("bookingReference", ref)
                        .param("lastName", "Manage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(ref))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    // ── 2. Valid reference + wrong lastName → 404 ──────────────────────────────

    @Test
    void manage_wrongLastName_returns404WithFriendlyMessage() throws Exception {
        String ref = createConfirmedBookingAndGetReference(960, 962, "Bob Lookup");

        mockMvc.perform(get("/api/bookings/manage")
                        .param("bookingReference", ref)
                        .param("lastName", "WrongName"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.message").value(
                        "We couldn't find a booking with these details. Please check your reference and last name."));
    }

    // ── 3. Unknown reference → 404 with friendly message ───────────────────────

    @Test
    void manage_unknownReference_returns404WithFriendlyMessage() throws Exception {
        mockMvc.perform(get("/api/bookings/manage")
                        .param("bookingReference", "RC-000000-XXXX")
                        .param("lastName", "Anyone"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.message").value(
                        "We couldn't find a booking with these details. Please check your reference and last name."));
    }

    // ── 4. Case-insensitive lastName match → 200 ───────────────────────────────

    @Test
    void manage_lastNameCaseInsensitive_returnsBooking() throws Exception {
        String ref = createConfirmedBookingAndGetReference(970, 972, "Carol Case");

        // "CASE" in all-caps should still match "Carol Case"
        mockMvc.perform(get("/api/bookings/manage")
                        .param("bookingReference", ref)
                        .param("lastName", "CASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(ref));
    }

    // ── 5–8. Accent-insensitive lastName matching ─────────────────────────────

    @Test
    void manage_accent_storedWithAccent_inputWithout() throws Exception {
        // "Nihan Güner" stored; input "Guner" (no umlaut) → must match
        String ref = createConfirmedBookingAndGetReference(980, 982, "Nihan Güner", "nihan.guner.acc1@test.com");

        mockMvc.perform(get("/api/bookings/manage")
                        .param("bookingReference", ref)
                        .param("lastName", "Guner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(ref));
    }

    @Test
    void manage_accent_storedWithout_inputWithAccent() throws Exception {
        // "Alice Guner" stored; input "Güner" (with umlaut) → must match
        String ref = createConfirmedBookingAndGetReference(984, 986, "Alice Guner", "alice.guner.acc2@test.com");

        mockMvc.perform(get("/api/bookings/manage")
                        .param("bookingReference", ref)
                        .param("lastName", "Güner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(ref));
    }

    @Test
    void manage_accent_lowercaseInput_returnsBooking() throws Exception {
        // "Carol Güner" stored; lowercase "guner" → must match (accent + case insensitive)
        String ref = createConfirmedBookingAndGetReference(988, 990, "Carol Güner", "carol.guner.acc3@test.com");

        mockMvc.perform(get("/api/bookings/manage")
                        .param("bookingReference", ref)
                        .param("lastName", "guner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(ref));
    }

    @Test
    void manage_accent_wrongLastName_returns404() throws Exception {
        // "Dave Güner" stored; completely wrong name → 404
        String ref = createConfirmedBookingAndGetReference(992, 994, "Dave Güner", "dave.guner.acc4@test.com");

        mockMvc.perform(get("/api/bookings/manage")
                        .param("bookingReference", ref)
                        .param("lastName", "Smith"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "We couldn't find a booking with these details. Please check your reference and last name."));
    }

    @Test
    void manage_extraSpacesInLastName_returnsBooking() throws Exception {
        // Input lastName with surrounding and internal spaces must still match.
        // Uses lastNameNormalized stored on the Customer entity (trimmed + collapsed).
        String ref = createConfirmedBookingAndGetReference(996, 998, "Eve Güner", "eve.guner.acc5@test.com");

        mockMvc.perform(get("/api/bookings/manage")
                        .param("bookingReference", ref)
                        .param("lastName", "  Guner  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(ref));
    }

    @Test
    void manage_validToken_returnsBookingDirectly() throws Exception {
        String ref = createConfirmedBookingAndGetReference(1000, 1002, "Token Direct", "token.direct@test.com");
        String token = issueManageToken(ref);

        mockMvc.perform(get("/api/bookings/manage/token")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(ref))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void manage_invalidToken_returnsFriendlyFallbackMessage() throws Exception {
        mockMvc.perform(get("/api/bookings/manage/token")
                        .param("token", "not-a-real-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.message").value(ManageBookingTokenService.INVALID_OR_EXPIRED_MESSAGE));
    }

    @Test
    void manage_expiredToken_returnsFriendlyFallbackMessage() throws Exception {
        String ref = createConfirmedBookingAndGetReference(1004, 1006, "Token Expired", "token.expired@test.com");
        String token = issueManageToken(ref);
        Booking booking = bookingRepository.findByBookingReferenceEager(ref).orElseThrow();
        booking.setManageTokenExpiresAt(Instant.now().minusSeconds(60));
        bookingRepository.saveAndFlush(booking);

        mockMvc.perform(get("/api/bookings/manage/token")
                        .param("token", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.message").value(ManageBookingTokenService.INVALID_OR_EXPIRED_MESSAGE));
    }



    /**
     * Creates a booking for the given customer name, processes payment (→ CONFIRMED),
     * and returns the generated bookingReference.
     */
    private String createConfirmedBookingAndGetReference(
            int pickupOffset, int dropoffOffset, String customerName) throws Exception {
        String email = customerName.toLowerCase().replace(" ", ".") + "@test.com";
        return createConfirmedBookingAndGetReference(pickupOffset, dropoffOffset, customerName, email);
    }

    private String issueManageToken(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReferenceEager(bookingReference).orElseThrow();
        return manageBookingTokenService.issueToken(booking);
    }

    private String createConfirmedBookingAndGetReference(
            int pickupOffset, int dropoffOffset, String customerName, String email) throws Exception {

        long carId = anyAvailableCarId(pickupOffset, dropoffOffset);

        // Create booking
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(pickupOffset), daysFromNow(dropoffOffset),
                                customerName, email)))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId   = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String reference = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        TestPaymentFixtures.confirmByVerifiedStripeWebhook(bookingRepository, paymentService, bookingId);

        return reference;
    }

    private long anyAvailableCarId(int pickupOffset, int dropoffOffset) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", daysFromNow(pickupOffset))
                        .param("dropoffDateTime", daysFromNow(dropoffOffset)))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).as("No cars available for offset [+%dd, +%dd]", pickupOffset, dropoffOffset)
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
                               String customerName, String email) {
        return """
                {
                  "carId": %d,
                  "customerName": "%s",
                  "customerEmail": "%s",
                  "customerPhone": "+34600000099",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport",
                  "dropoffLocation": "City Centre",
                  "insurancePackageId": 1
                }
                """.formatted(carId, customerName, email, pickup, dropoff);
    }

}
