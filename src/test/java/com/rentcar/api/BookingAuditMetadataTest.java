package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingActorType;
import com.rentcar.api.domain.booking.BookingChannel;
import com.rentcar.api.repository.BookingRepository;
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
 * Integration tests verifying booking audit metadata is set correctly.
 *
 * Covered scenarios:
 *  1. Booking creation sets createdByType=CUSTOMER_ANONYMOUS and createdChannel=WEB
 *     (verified via both the API response and direct entity inspection)
 *  2. Manage-booking cancellation sets cancelledByType=CUSTOMER_ANONYMOUS,
 *     cancelledChannel=WEB, cancelledAt non-null, and the correct cancellationReason
 *
 * Uses date windows starting at 1300 days to avoid conflicts with:
 *   ManageCancelTest: 1200–1242, CancellationPolicyTest: 1100–1152,
 *   ManageBookingTest: 950–994, MockPaymentFlowTest: 900–922,
 *   TransferBookingControllerTest: 800–870.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingAuditMetadataTest {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired MockMvc mockMvc;
    @Autowired BookingRepository bookingRepository;

    // ── Test 1: creation audit metadata ──────────────────────────────────────

    @Test
    void bookingCreation_setsCreatedByTypeAndChannel() throws Exception {
        long carId = anyAvailableCarId(1300, 1301);

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1300), daysFromNow(1301),
                                "Audit Test User", "auditcreate@example.com")))
                .andExpect(status().isOk())
                // Audit fields are exposed in the response
                .andExpect(jsonPath("$.createdByType").value("CUSTOMER_ANONYMOUS"))
                .andExpect(jsonPath("$.createdChannel").value("WEB"))
                .andReturn();

        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        // Also verify at the entity level — the source of truth
        Booking saved = bookingRepository.findById(id).orElseThrow();
        assertThat(saved.getCreatedByType()).isEqualTo(BookingActorType.CUSTOMER_ANONYMOUS);
        assertThat(saved.getCreatedChannel()).isEqualTo(BookingChannel.WEB);
        // Cancellation fields should be null until cancellation occurs
        assertThat(saved.getCancelledByType()).isNull();
        assertThat(saved.getCancelledChannel()).isNull();
        assertThat(saved.getCancelledAt()).isNull();
        assertThat(saved.getCancellationReason()).isNull();
    }

    // ── Test 2: manage-booking cancellation audit metadata ───────────────────

    @Test
    void manageCancellation_setsCancelledAuditFields() throws Exception {
        long carId = anyAvailableCarId(1310, 1311);

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1310), daysFromNow(1311),
                                "Cancel Audit", "auditcancel@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        String ref = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingReference");
        long id  = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        // Perform cancellation
        mockMvc.perform(post("/api/bookings/manage/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingReference\":\"%s\",\"lastName\":\"Audit\"}".formatted(ref)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledByType").value("CUSTOMER_ANONYMOUS"))
                .andExpect(jsonPath("$.cancelledChannel").value("WEB"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.cancellationReason")
                        .value("Customer requested cancellation from manage booking"));

        // Verify at the entity level
        Booking cancelled = bookingRepository.findById(id).orElseThrow();
        assertThat(cancelled.getCancelledByType()).isEqualTo(BookingActorType.CUSTOMER_ANONYMOUS);
        assertThat(cancelled.getCancelledChannel()).isEqualTo(BookingChannel.WEB);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getCancellationReason())
                .isEqualTo("Customer requested cancellation from manage booking");
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
                  "customerPhone": "+34600000099",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport T1",
                  "dropoffLocation": "Airport T1"
                }
                """.formatted(carId, name, email, pickup, dropoff);
    }
}
