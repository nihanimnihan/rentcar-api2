package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.email.EmailService;
import com.rentcar.api.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that a confirmation email failure does NOT rollback or prevent the
 * booking from reaching CONFIRMED status.
 *
 * Uses {@code @MockBean EmailService} to replace the dev-profile FakeEmailService
 * with a mock that throws on every call — isolating the email failure scenario
 * without polluting the real FakeEmailService.
 *
 * Date window 1500+ (separate from BookingEmailNotificationTest at 1400+).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingEmailFailureResilienceTest {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired MockMvc mockMvc;
    @Autowired BookingRepository bookingRepository;

    /** Replaces FakeEmailService — throws on every sendBookingConfirmation() call. */
    @MockBean EmailService emailService;

    @Test
    void emailFailure_doesNotRollbackConfirmedBooking() throws Exception {
        // Arrange: configure mock to throw on any email attempt
        doThrow(new RuntimeException("Simulated SMTP failure"))
                .when(emailService).sendBookingConfirmation(any());

        long carId = anyAvailableCarId(1500, 1501);

        // Create booking
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1500), daysFromNow(1501),
                                "Resilience Test", "resiliencetest@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        // Process payment — email will throw, but booking must still be CONFIRMED
        mockMvc.perform(post("/api/bookings/" + bookingId + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"pm_test_valid\"}"))
                .andExpect(status().isOk())
                // Response must indicate CONFIRMED despite email failure
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Verify at entity level — booking must be persisted as CONFIRMED
        var savedBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
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
                  "customerPhone": "+34600000066",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport T2",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, name, email, pickup, dropoff);
    }
}
