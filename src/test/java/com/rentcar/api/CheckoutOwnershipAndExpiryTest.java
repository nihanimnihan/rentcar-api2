package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.payment.provider.FakePaymentProvider;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.scheduler.BookingExpiryScheduler;
import com.rentcar.api.util.AppClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests covering checkout session ownership, expiry and availability rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CheckoutOwnershipAndExpiryTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingExpiryScheduler bookingExpiryScheduler;

    @Autowired
    private AppClock appClock;

    private String lastCheckoutToken;

    // Helpers copied from existing tests for consistency
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
        return LocalDateTime.now().plusDays(days).withHour(10).withMinute(0).withSecond(0).withNano(0).format(FMT);
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

    private String bookingBody(long carId, String pickup, String dropoff) {
        return """
                {
                  "carId": %d,
                  "customerName": "Ownership Test User",
                  "customerEmail": "ownership-test@example.com",
                  "customerPhone": "+34600000011",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Airport T1",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(carId, pickup, dropoff);
    }

    @Test
    void createBooking_returnsHeader_and_setsPending_withExpiry_andToken() throws Exception {
        long carId = anyAvailableCarId(1800, 1802);
        MvcResult r = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1800), daysFromNow(1802))))
                .andExpect(status().isOk())
                .andReturn();

        String token = r.getResponse().getHeader("X-Checkout-Session-Token");
        assertThat(token).as("checkout token header must be present").isNotNull();

        long id = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();

        // Fetch raw entity to assert internal fields not exposed via API
        Booking booking = bookingRepository.findById(id).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getExpiresAt()).isNotNull();
        assertThat(booking.getCheckoutSessionToken()).isNotNull();
    }

    @Test
    void paymentIntent_missingOrWrong_token_isRejected() throws Exception {
        long carId = anyAvailableCarId(1810, 1812);
        long id = createBooking(carId, daysFromNow(1810), daysFromNow(1812));
        Booking booking = bookingRepository.findById(id).orElseThrow();
        String realToken = booking.getCheckoutSessionToken();
        // Missing token
        mockMvc.perform(post("/api/bookings/" + id + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // Wrong token
        mockMvc.perform(post("/api/bookings/" + id + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checkoutSessionToken\": \"wrong-token-123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // Correct token should succeed (sanity check)
        mockMvc.perform(post("/api/bookings/" + id + "/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checkoutSessionToken\": \"" + realToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReference").value(matchesPattern("PAY-[0-9A-F]{8}")));
    }

    @Test
    void expiredPendingBooking_isMarkedExpired_byScheduler_andDoesNotBlockAvailability() throws Exception {
        long carId = anyAvailableCarId(1820, 1822);
        long id = createBooking(carId, daysFromNow(1820), daysFromNow(1822));

        Booking b = bookingRepository.findById(id).orElseThrow();
        // Force expiration: set expiresAt in the past
        b.setExpiresAt(appClock.nowUtc().minusSeconds(60));
        bookingRepository.save(b);

        // Run scheduler directly
        bookingExpiryScheduler.expirePendingBookings();

        Booking expired = bookingRepository.findById(id).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(expired.getExpiresAt()).isNull();
        assertThat(expired.getCheckoutSessionToken()).isNull();

        // Car should be available for same window (expired booking must not block)
        MvcResult search = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", daysFromNow(1820))
                        .param("dropoffDateTime", daysFromNow(1822)))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> ids = JsonPath.read(search.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).contains((int)carId);
    }

    @Test
    void failedBooking_doesNotBlock_availability_and_pendingHolds_block_others() throws Exception {
        long carId = anyAvailableCarId(1830, 1832);
        long id = createBooking(carId, daysFromNow(1830), daysFromNow(1832));

        // Force a failed payment
        mockMvc.perform(post("/api/bookings/" + id + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(FakePaymentProvider.FORCE_FAIL_METHOD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // After FAILED, car should be available (FAILED does not block)
        MvcResult search = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", daysFromNow(1830))
                        .param("dropoffDateTime", daysFromNow(1832)))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> ids = JsonPath.read(search.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).contains((int)carId);

        // New PENDING booking for same car should be rejected while an active PENDING hold exists
        long otherCarId = anyAvailableCarId(1840, 1842); // pick any available car for a different window
        // Create a fresh booking that holds its car — reuse carId from first booking
        long holderBookingId = createBooking(carId, daysFromNow(1850), daysFromNow(1852));

        // Attempt to create overlapping booking for same car/time as holderBookingId — should 409
        MvcResult conflict = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(carId, daysFromNow(1850), daysFromNow(1852))))
                .andExpect(status().isConflict())
                .andReturn();
    }

    @Test
    void successfulPayment_clearsExpiresAndToken_and_marksConfirmed() throws Exception {
        long carId = anyAvailableCarId(1860, 1862);
        long id = createBooking(carId, daysFromNow(1860), daysFromNow(1862));

        // Process payment successfully
        mockMvc.perform(post("/api/bookings/" + id + "/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("pm_valid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Booking booking = bookingRepository.findById(id).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getExpiresAt()).isNull();
        assertThat(booking.getCheckoutSessionToken()).isNull();
    }

    private String payBody(String paymentMethodId) {
        return "{\"paymentMethodId\":\"" + paymentMethodId + "\",\"checkoutSessionToken\":\"" + lastCheckoutToken + "\"}";
    }
}
