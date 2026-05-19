package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
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
 * Integration tests for booking creation transaction safety.
 *
 * BookingService.createBooking() is already @Transactional and acquires a
 * PESSIMISTIC_WRITE lock on the car row (SELECT FOR UPDATE) before the overlap
 * check, making the check + insert atomic for concurrent requests targeting the
 * same car.
 *
 * These tests verify the observable outcomes of that safety contract:
 * - a valid booking is persisted
 * - an overlapping active booking blocks a second attempt (409)
 * - a cancelled booking does not block a new one
 *
 * Uses unique far-future date windows (700+, 710+, 720+, 730+ days) to avoid
 * conflicts with other tests sharing the same H2 in-memory database.
 *
 * TODO (post-MVP): add a true concurrency test that fires two threads with
 *   overlapping requests simultaneously and asserts exactly one succeeds and
 *   one gets 409. This requires careful thread synchronisation or a dedicated
 *   @Testcontainers PostgreSQL instance (H2 does not honour SELECT FOR UPDATE
 *   the same way PostgreSQL does under real concurrency).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingTransactionTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";

    @Autowired
    private MockMvc mockMvc;

    // ── 1. Successful persistence ─────────────────────────────────────────────

    @Test
    void createBooking_persistsSuccessfully() throws Exception {
        long carId = anyAvailableCarId(700, 702);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(700), daysFromNow(702))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ── 2. Active booking blocks creation ────────────────────────────────────

    @Test
    void createBooking_overlappingActiveBooking_returns409() throws Exception {
        String pickup  = daysFromNow(710);
        String dropoff = daysFromNow(712);
        long carId = anyAvailableCarId(710, 712);

        // First booking — must succeed
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, pickup, dropoff)))
                .andExpect(status().isOk());

        // Second booking for same car, same window — must be rejected
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, pickup, dropoff)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Car not available"));
    }

    @Test
    void createBooking_partialOverlapWithActiveBooking_returns409() throws Exception {
        long carId = anyAvailableCarId(720, 723);

        // Book [720, 722]
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(720), daysFromNow(722))))
                .andExpect(status().isOk());

        // Attempt [721, 723] — starts inside the existing booking (partial overlap)
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(721), daysFromNow(723))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Car not available"));
    }

    // ── 3. Cancelled booking does not block ───────────────────────────────────

    @Test
    void createBooking_afterCancelledBooking_succeeds() throws Exception {
        String pickup  = daysFromNow(730);
        String dropoff = daysFromNow(732);
        long carId = anyAvailableCarId(730, 732);

        // Create booking then cancel it
        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, pickup, dropoff)))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk());

        // New booking for the same car and window must now succeed
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, pickup, dropoff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the ID of the first car available in the given window.
     * Fails fast if no cars are available (seed data issue).
     */
    private long anyAvailableCarId(int pickupDayOffset, int dropoffDayOffset) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", daysFromNow(pickupDayOffset))
                        .param("dropoffDateTime", daysFromNow(dropoffDayOffset)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).as("No cars available in window [+%dd, +%dd] — check seed data",
                pickupDayOffset, dropoffDayOffset).isNotEmpty();
        return ids.get(0).longValue();
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
                  "customerName": "Transaction Test",
                  "customerEmail": "transaction-test@example.com",
                  "customerPhone": "+34600000088",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "City Centre",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, pickup, dropoff);
    }
}
