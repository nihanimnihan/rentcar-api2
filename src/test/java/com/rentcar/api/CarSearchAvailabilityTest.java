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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for car search availability filtering.
 *
 * Verifies that GET /api/cars/search correctly excludes cars with overlapping
 * PENDING or CONFIRMED bookings, and that CANCELLED bookings do not block
 * availability.
 *
 * Uses unique far-future date windows (600+, 610+, 620+ days) to avoid
 * conflicts with other tests that share the same H2 in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CarSearchAvailabilityTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    // Credentials from application.yaml — matches what SecurityIntegrationTest uses
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchCars_availableCar_isIncludedInResults() throws Exception {
        mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", daysFromNow(600))
                        .param("dropoffDateTime", daysFromNow(602)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    void searchCars_carWithOverlappingPendingBooking_isExcluded() throws Exception {
        String pickup  = daysFromNow(610);
        String dropoff = daysFromNow(612);

        // Identify which cars are available before any booking exists in this window
        MvcResult before = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", pickup)
                        .param("dropoffDateTime", dropoff))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> idsBefore = JsonPath.read(before.getResponse().getContentAsString(), "$[*].id");
        assertThat(idsBefore).isNotEmpty();

        // Book the first available car — new booking lands in PENDING status
        long bookedCarId = idsBefore.get(0).longValue();
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(bookedCarId, pickup, dropoff)))
                .andExpect(status().isOk());

        // Re-search the same window — the booked car must be absent
        MvcResult after = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", pickup)
                        .param("dropoffDateTime", dropoff))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> idsAfter = JsonPath.read(after.getResponse().getContentAsString(), "$[*].id");
        assertThat(idsAfter).doesNotContain((int) bookedCarId);
    }

    @Test
    void searchCars_cancelledBooking_doesNotBlockAvailability() throws Exception {
        String pickup  = daysFromNow(620);
        String dropoff = daysFromNow(622);

        // Find a car available in this window
        MvcResult before = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", pickup)
                        .param("dropoffDateTime", dropoff))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> idsBefore = JsonPath.read(before.getResponse().getContentAsString(), "$[*].id");
        assertThat(idsBefore).isNotEmpty();

        long bookedCarId = idsBefore.get(0).longValue();

        // Create a PENDING booking, then immediately cancel it
        MvcResult bookingResult = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(bookedCarId, pickup, dropoff)))
                .andExpect(status().isOk())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(
                bookingResult.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk());

        // After cancellation the car must appear in the same window again
        MvcResult after = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", pickup)
                        .param("dropoffDateTime", dropoff))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> idsAfter = JsonPath.read(after.getResponse().getContentAsString(), "$[*].id");
        assertThat(idsAfter).contains((int) bookedCarId);
    }

    @Test
    void searchCars_bookingEndingExactlyAtPickup_doesNotBlockAvailability() throws Exception {
        // Window split at day 632:
        //   existingBooking : [day630, day632]   (ends exactly at our search pickup)
        //   searchWindow    : [day632, day634]
        // Overlap condition is strict (dropoffDateTime > pickupDateTime),
        // so a booking ending at T does NOT block a search starting at T.
        String bookedPickup  = daysFromNow(630);
        String bookedDropoff = daysFromNow(632);
        String searchPickup  = daysFromNow(632);   // identical to bookedDropoff
        String searchDropoff = daysFromNow(634);

        // Find a car available across the full span so we can book it in the first half
        MvcResult scan = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", bookedPickup)
                        .param("dropoffDateTime", bookedDropoff))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(scan.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).isNotEmpty();
        long carId = ids.get(0).longValue();

        // Create the adjacent-before booking
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, bookedPickup, bookedDropoff)))
                .andExpect(status().isOk());

        // Search the adjacent-after window — the car must still appear
        MvcResult result = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", searchPickup)
                        .param("dropoffDateTime", searchDropoff))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> found = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        assertThat(found).contains((int) carId);
    }

    @Test
    void searchCars_bookingStartingExactlyAtDropoff_doesNotBlockAvailability() throws Exception {
        // Window split at day 642:
        //   searchWindow    : [day640, day642]
        //   existingBooking : [day642, day644]   (starts exactly at our search dropoff)
        // Overlap condition is strict (pickupDateTime < dropoffDateTime),
        // so a booking starting at T does NOT block a search ending at T.
        String searchPickup  = daysFromNow(640);
        String searchDropoff = daysFromNow(642);
        String bookedPickup  = daysFromNow(642);   // identical to searchDropoff
        String bookedDropoff = daysFromNow(644);

        // Find a car available in the booking window so we can book it in the second half
        MvcResult scan = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", bookedPickup)
                        .param("dropoffDateTime", bookedDropoff))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(scan.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).isNotEmpty();
        long carId = ids.get(0).longValue();

        // Create the adjacent-after booking
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, bookedPickup, bookedDropoff)))
                .andExpect(status().isOk());

        // Search the adjacent-before window — the car must still appear
        MvcResult result = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", searchPickup)
                        .param("dropoffDateTime", searchDropoff))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> found = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        assertThat(found).contains((int) carId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
                  "customerName": "Availability Test",
                  "customerEmail": "availability-test@example.com",
                  "customerPhone": "+34600000099",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "City Centre",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, pickup, dropoff);
    }
}
