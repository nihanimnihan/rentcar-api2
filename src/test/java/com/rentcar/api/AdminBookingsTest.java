package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.addon.Addon;
import com.rentcar.api.domain.addon.AddonPricingType;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.handover.BookingDepositStatus;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.email.FakeEmailService;
import com.rentcar.api.repository.AddonRepository;
import com.rentcar.api.repository.BookingDepositRepository;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /api/admin/bookings.
 *
 * Uses far-future date windows (990+) to avoid conflicts with other tests
 * sharing the same H2 in-memory database.
 */
@SpringBootTest(properties = "app.public-base-url=http://localhost:8091")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBookingsTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FakeEmailService fakeEmailService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingDepositRepository bookingDepositRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private AddonRepository addonRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearEmails() {
        fakeEmailService.clearSentEmails();
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Test
    void listBookings_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void listBookings_withWrongCredentials_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listBookings_withAdminCredentials_returnsOkAndArray() throws Exception {
        mockMvc.perform(get("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void bookingsFrontend_usesDetailModalInsteadOfRawApiLink() throws Exception {
        mockMvc.perform(get("/admin/bookings.html")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("booking-detail-overlay")))
                .andExpect(content().string(containsString("admin-bookings.js")));

        mockMvc.perform(get("/js/admin-bookings.js")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-booking-detail-id")))
                .andExpect(content().string(not(containsString("href=\"/api/admin/bookings/"))))
                .andExpect(content().string(not(containsString("target=\"_blank\""))));
    }

    // ── Admin create booking ────────────────────────────────────────────────

    @Test
    void createBooking_withCash_succeedsSendsEmailAndGeneratesManageToken() throws Exception {
        long carId = anyAvailableCarId(daysFromNow(1600), daysFromNow(1602));

        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1600, 1602, "CASH", "350.00", List.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.source").value("OFFICE"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.totalPrice").value(350.00))
                .andReturn();

        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getCheckoutSessionToken()).isNull();
        assertThat(booking.getManageTokenHash()).isNotBlank();
        assertThat(booking.getNotes())
                .contains("Desk reservation")
                .contains("Manual price override active");
        assertThat(fakeEmailService.getSentEmails()).hasSize(1);
        assertThat(fakeEmailService.getSentEmails().get(0).managementUrl()).contains("/manage-booking.html?token=");

        var payment = paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    void handover_requiresCollectedDepositAndSignature_thenMarksPickedUp() throws Exception {
        long carId = anyAvailableCarId(daysFromNow(1660), daysFromNow(1662));
        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1660, 1662, "CASH", "350.00", List.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();
        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(post("/api/admin/bookings/{id}/handover", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kmOut": 12345,
                                  "fuelLevelOut": "FULL",
                                  "batteryLevelOut": "NOT_APPLICABLE",
                                  "customerSignatureData": "data:image/png;base64,abc123",
                                  "notes": "Tablet handover"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerSignaturePresent").value(true));

        mockMvc.perform(post("/api/admin/bookings/{id}/picked-up", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("collected deposit")));

        mockMvc.perform(post("/api/admin/bookings/{id}/deposit/manual-collection", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "CARD_TERMINAL",
                                  "note": "Terminal receipt 42"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLLECTED"))
                .andExpect(jsonPath("$.amount").value(750.00))
                .andExpect(jsonPath("$.remainingAmount").value(750.00))
                .andExpect(jsonPath("$.refundDeadlineAt").exists());

        mockMvc.perform(post("/api/admin/bookings/{id}/picked-up", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.canMarkPickedUp").value(false));

        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PICKED_UP);
        var deposit = bookingDepositRepository.findByBooking(booking).orElseThrow();
        assertThat(deposit.getStatus()).isEqualTo(BookingDepositStatus.COLLECTED);
        assertThat(deposit.getRefundDeadlineAt()).isEqualTo(deposit.getCollectedAt().plus(java.time.Duration.ofDays(45)));
    }

    @Test
    void createBooking_withOfficeAndCardTerminal_markPaidWithExpectedMethod() throws Exception {
        assertAdminPaymentMethod(1603, "OFFICE", PaymentMethod.OFFICE);
        assertAdminPaymentMethod(1606, "CARD_TERMINAL", PaymentMethod.CARD_TERMINAL);
        assertAdminPaymentMethod(1609, "STRIPE", PaymentMethod.STRIPE);
    }

    @Test
    void createBooking_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"));
    }

    @Test
    void createBooking_invalidDates_returns400() throws Exception {
        long carId = anyAvailableCarId(daysFromNow(1610), daysFromNow(1612));

        mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1612, 1610, "CASH", "200.00", List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid booking dates"));
    }

    @Test
    void createBooking_unavailableVehicleRejected() throws Exception {
        LocalDateTime pickup = daysFromNow(1614);
        LocalDateTime dropoff = daysFromNow(1616);
        long carId = anyAvailableCarId(pickup, dropoff);
        createBookingWithCar(carId, pickup, dropoff);

        mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1614, 1616, "CASH", "200.00", List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Car not available"));
    }

    @Test
    void createBooking_overlappingAdminBookingRejected() throws Exception {
        long carId = anyAvailableCarId(daysFromNow(1618), daysFromNow(1620));
        mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1618, 1620, "CASH", "220.00", List.of())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1619, 1621, "OFFICE", "240.00", List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Car not available"));
    }

    @Test
    void createBooking_withNoAddons_storesEmptySnapshot() throws Exception {
        long carId = anyAvailableCarId(daysFromNow(1622), daysFromNow(1624));

        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1622, 1624, "CASH", "300.00", List.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addons").isEmpty())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getBookingAddons()).isEmpty();
        assertThat(booking.getAddonCharge()).isEqualByComparingTo("0.00");
    }

    @Test
    void createBooking_withOneAddon_snapshotsAddon() throws Exception {
        long addonId = activeAddonIds().get(0);
        long carId = anyAvailableCarId(daysFromNow(1626), daysFromNow(1628));

        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1626, 1628, "CASH", "400.00", List.of(addonId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addons.length()").value(1))
                .andReturn();

        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getBookingAddons()).hasSize(1);
        var snapshot = booking.getBookingAddons().get(0);
        assertThat(snapshot.getAddonName()).isNotBlank();
        assertThat(snapshot.getPriceAtBooking()).isPositive();
    }

    @Test
    void createBooking_withMultipleAddons_snapshotsAll() throws Exception {
        List<Long> addonIds = activeAddonIds().subList(0, 2);
        long carId = anyAvailableCarId(daysFromNow(1630), daysFromNow(1632));

        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1630, 1632, "CARD_TERMINAL", "500.00", addonIds)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addons.length()").value(2))
                .andReturn();

        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getBookingAddons()).hasSize(2);
    }

    @Test
    void createBooking_selectedInactiveAddonRejected() throws Exception {
        Addon inactive = addonRepository.save(Addon.builder()
                .name("Inactive Admin Test")
                .code("INACTIVE_ADMIN_TEST")
                .price(new java.math.BigDecimal("9.00"))
                .pricingType(AddonPricingType.ONE_TIME)
                .active(false)
                .build());
        long carId = anyAvailableCarId(daysFromNow(1634), daysFromNow(1636));

        mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1634, 1636, "CASH", "300.00", List.of(inactive.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selected add-on is inactive."));
    }

    @Test
    void createBooking_persistsRichPickupAndReturnLocations() throws Exception {
        long carId = anyAvailableCarId(daysFromNow(1640), daysFromNow(1642));

        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBodyWithLocations(carId, 1640, 1642, "CASH", "190.00",
                                "Hotel Arts Barcelona",
                                "Carrer de la Marina, 19-21, 08005 Barcelona, Spain",
                                "pickup-place-123",
                                "BCN Airport T2",
                                "Terminal 2, 08820 El Prat de Llobregat, Barcelona, Spain",
                                "return-place-456")))
                .andExpect(status().isCreated())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getPickupLocation()).isEqualTo("Hotel Arts Barcelona");
        assertThat(booking.getPickupAddress()).isEqualTo("Carrer de la Marina, 19-21, 08005 Barcelona, Spain");
        assertThat(booking.getPickupPlaceId()).isEqualTo("pickup-place-123");
        assertThat(booking.getDropoffLocation()).isEqualTo("BCN Airport T2");
        assertThat(booking.getDropoffAddress()).isEqualTo("Terminal 2, 08820 El Prat de Llobregat, Barcelona, Spain");
        assertThat(booking.getDropoffPlaceId()).isEqualTo("return-place-456");

        mockMvc.perform(get("/api/admin/bookings/{id}", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupLocation").value("Hotel Arts Barcelona"))
                .andExpect(jsonPath("$.pickupAddress").value("Carrer de la Marina, 19-21, 08005 Barcelona, Spain"))
                .andExpect(jsonPath("$.pickupPlaceId").value("pickup-place-123"))
                .andExpect(jsonPath("$.dropoffLocation").value("BCN Airport T2"))
                .andExpect(jsonPath("$.dropoffAddress").value("Terminal 2, 08820 El Prat de Llobregat, Barcelona, Spain"))
                .andExpect(jsonPath("$.dropoffPlaceId").value("return-place-456"));
    }

    @Test
    void createBooking_withoutAddressMetadata_keepsLocationLabelsOnly() throws Exception {
        long carId = anyAvailableCarId(daysFromNow(1644), daysFromNow(1646));

        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBodyWithLocations(carId, 1644, 1646, "OFFICE", "190.00",
                                "Airport Desk", "", "", "Airport Desk", " ", "")))
                .andExpect(status().isCreated())
                .andReturn();

        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getPickupLocation()).isEqualTo("Airport Desk");
        assertThat(booking.getPickupAddress()).isNull();
        assertThat(booking.getPickupPlaceId()).isNull();
        assertThat(booking.getDropoffLocation()).isEqualTo("Airport Desk");
        assertThat(booking.getDropoffAddress()).isNull();
        assertThat(booking.getDropoffPlaceId()).isNull();
    }

    @Test
    void createBooking_whenTotalMatchesCalculation_doesNotAddOverrideNote() throws Exception {
        long carId = anyAvailableCarId(daysFromNow(1648), daysFromNow(1650));

        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1648, 1650, "CASH", "190.00", List.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(190.00))
                .andReturn();

        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getRentalCharge()).isEqualByComparingTo("190.00");
        assertThat(booking.getTax()).isEqualByComparingTo("0.00");
        assertThat(booking.getTotalPrice()).isEqualByComparingTo("190.00");
        assertThat(booking.getNotes()).isEqualTo("Desk reservation");
    }

    @Test
    void createBooking_whenManualOverrideDiffersFromCalculation_recordsAdminOnlyNote() throws Exception {
        long carId = anyAvailableCarId(daysFromNow(1652), daysFromNow(1654));

        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1652, 1654, "CASH", "150.00", List.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(150.00))
                .andReturn();

        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getNotes())
                .contains("Desk reservation")
                .contains("Manual price override active: calculated 190.00 EUR, final 150.00 EUR.");
        assertThat(fakeEmailService.getSentConfirmationEmails()).hasSize(1);
        assertThat(fakeEmailService.getSentConfirmationEmails().get(0).toString())
                .doesNotContain("Manual price override active");
    }

    @Test
    void createBooking_withAddonsIncludesAddonTotalInExpectedCalculation() throws Exception {
        Addon daily = addonRepository.save(Addon.builder()
                .name("Daily Admin Pricing Test")
                .code("DAILY_ADMIN_PRICING_TEST")
                .price(new java.math.BigDecimal("10.00"))
                .pricingType(AddonPricingType.DAILY)
                .active(true)
                .build());
        Addon oneTime = addonRepository.save(Addon.builder()
                .name("One Time Admin Pricing Test")
                .code("ONE_TIME_ADMIN_PRICING_TEST")
                .price(new java.math.BigDecimal("15.00"))
                .pricingType(AddonPricingType.ONE_TIME)
                .active(true)
                .build());
        long carId = anyAvailableCarId(daysFromNow(1656), daysFromNow(1658));

        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, 1656, 1658, "CARD_TERMINAL", "225.00",
                                List.of(daily.getId(), oneTime.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(225.00))
                .andExpect(jsonPath("$.addons.length()").value(2))
                .andReturn();

        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertThat(booking.getAddonCharge()).isEqualByComparingTo("35.00");
        assertThat(booking.getTotalPrice()).isEqualByComparingTo("225.00");
        assertThat(booking.getNotes()).isEqualTo("Desk reservation");
    }

    // ── Response shape ────────────────────────────────────────────────────────

    @Test
    void listBookings_responseContainsExpectedFields() throws Exception {
        // Create a booking so we have at least one item with known shape.
        long bookingId = createBooking(daysFromNow(990), daysFromNow(992));

        MvcResult result = mockMvc.perform(get("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        List<Object> items = JsonPath.read(result.getResponse().getContentAsString(), "$");
        assertThat(items).isNotEmpty();

        // Verify the first item (newest) has all required fields.
        String json = result.getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(json, "$[0].id")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].bookingReference")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].status")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].customerName")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].customerEmail")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].carBrand")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].carModel")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].pickupDateTime")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].dropoffDateTime")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$[0].totalPrice")).isNotNull();
        assertThat(json).contains(
                "\"source\"",
                "\"rentalCharge\"",
                "\"oneWayFee\"",
                "\"premiumLocationFee\"",
                "\"tax\"",
                "\"addonCharge\"",
                "\"bookingOptionType\"",
                "\"bookingOptionDailyFee\"",
                "\"cancellationPolicyType\"",
                "\"cancellationAllowed\"",
                "\"refundEligible\"",
                "\"refundAmount\"",
                "\"noShow\"");
        // paymentStatus may be null before a payment intent is created — field must be present.
        assertThat(json).contains("\"paymentStatus\"");
        assertThat(json).contains("\"paymentMethod\"");
    }

    @Test
    void detailBooking_responseContainsPricingAndPolicySnapshot() throws Exception {
        long bookingId = createBooking(daysFromNow(991), daysFromNow(992));

        mockMvc.perform(get("/api/admin/bookings/{id}", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) bookingId))
                .andExpect(jsonPath("$.rentalDays").isNumber())
                .andExpect(jsonPath("$.baseDailyPrice").isNumber())
                .andExpect(jsonPath("$.effectiveDailyPrice").isNumber())
                .andExpect(jsonPath("$.discountPercentage").isNumber())
                .andExpect(jsonPath("$.rentalCharge").isNumber())
                .andExpect(jsonPath("$.oneWayFee").isNumber())
                .andExpect(jsonPath("$.premiumLocationFee").isNumber())
                .andExpect(jsonPath("$.tax").isNumber())
                .andExpect(jsonPath("$.addonCharge").isNumber())
                .andExpect(jsonPath("$.bookingOptionType").value("BEST_PRICE"))
                .andExpect(jsonPath("$.bookingOptionDailyFee").value(0.0))
                .andExpect(jsonPath("$.cancellationPolicyType").value("STRICT"))
                .andExpect(jsonPath("$.cancellationAllowed").isBoolean())
                .andExpect(jsonPath("$.adminOperationalCancellationAllowed").isBoolean())
                .andExpect(jsonPath("$.refundEligible").isBoolean())
                .andExpect(jsonPath("$.refundAmount").isNumber())
                .andExpect(jsonPath("$.cancellationPolicyMessage").isString())
                .andExpect(jsonPath("$.noShow").isBoolean());
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    void listBookings_activeBookingsUseNearestPickupBeforeNewest() throws Exception {
        long firstBookingId  = createBooking(daysFromNow(993), daysFromNow(995));
        long secondBookingId = createBooking(daysFromNow(996), daysFromNow(998));

        MvcResult result = mockMvc.perform(get("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        int idxFirst  = ids.indexOf((int) firstBookingId);
        int idxSecond = ids.indexOf((int) secondBookingId);

        assertThat(idxFirst).isLessThan(idxSecond);
    }

    @Test
    void markNoShow_cancelsBookingWithoutRefundAndSendsNoShowEmail() throws Exception {
        long bookingId = createAdminPaidBooking(1660, 1662, "CASH", "330.00");

        mockMvc.perform(post("/api/admin/bookings/{id}/no-show", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("NO_SHOW"))
                .andExpect(jsonPath("$.paymentStatus").value("NO_REFUND"));

        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        var payment = paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking).orElseThrow();
        assertThat(booking.getCancellationReason()).isEqualTo("NO_SHOW");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.NO_REFUND);
        assertThat(payment.getProviderReference()).isEqualTo("ADMIN-CASH");
        assertThat(fakeEmailService.getSentNoShowEmails()).hasSize(1);
        assertThat(fakeEmailService.getSentCancellationEmails()).isEmpty();

        mockMvc.perform(get("/api/admin/bookings/{id}", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noShow").value(true))
                .andExpect(jsonPath("$.refundEligible").value(false))
                .andExpect(jsonPath("$.refundAmount").value(0.00));
    }

    @Test
    void adminCancelLateBookingAllowedButDoesNotRefund() throws Exception {
        long bookingId = createAdminPaidBooking(1, 2, "CASH", "240.00");
        ageBookingCreatedAt(bookingId, Instant.now().minusSeconds(2 * 60 * 60));

        mockMvc.perform(post("/api/admin/bookings/{id}/cancel", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("NO_REFUND"));

        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        var payment = paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking).orElseThrow();
        assertThat(booking.getCancelledByType().name()).isEqualTo("ADMIN");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.NO_REFUND);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long createBooking(LocalDateTime pickup, LocalDateTime dropoff) throws Exception {
        long carId = anyAvailableCarId(pickup, dropoff);
        return createBookingWithCar(carId, pickup, dropoff);
    }

    private long createAdminPaidBooking(int pickupDays, int dropoffDays, String paymentSource, String totalPrice) throws Exception {
        long carId = anyAvailableCarId(daysFromNow(pickupDays), daysFromNow(dropoffDays));
        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, pickupDays, dropoffDays, paymentSource, totalPrice, List.of())))
                .andExpect(status().isCreated())
                .andReturn();
        fakeEmailService.clearSentEmails();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void ageBookingCreatedAt(long bookingId, Instant createdAt) {
        jdbcTemplate.update("UPDATE bookings SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt), bookingId);
    }

    private long createBookingWithCar(long carId, LocalDateTime pickup, LocalDateTime dropoff) throws Exception {
        String body = """
                {
                  "carId": %d,
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "Test Location",
                  "dropoffLocation": "Test Location",
                  "insurancePackageId": 1,
                  "customerName": "Admin Test",
                  "customerEmail": "admintest@example.com",
                  "customerPhone": "+34600000000",
                  "mileageOption": "INCLUDED"
                }
                """.formatted(carId, FMT.format(pickup), FMT.format(dropoff));

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void assertAdminPaymentMethod(int startDay, String paymentSource, PaymentMethod expectedMethod) throws Exception {
        long carId = anyAvailableCarId(daysFromNow(startDay), daysFromNow(startDay + 2));
        MvcResult result = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(carId, startDay, startDay + 2, paymentSource, "275.00", List.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value(expectedMethod.name()))
                .andReturn();
        long bookingId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var booking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        var payment = paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getMethod()).isEqualTo(expectedMethod);

        mockMvc.perform(get("/api/admin/bookings/{id}", bookingId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value(expectedMethod.name()));
    }

    private List<Long> activeAddonIds() {
        return addonRepository.findByActiveTrue().stream()
                .map(Addon::getId)
                .toList();
    }

    private String adminBookingBody(long carId, int pickupDays, int dropoffDays, String paymentSource, String totalPrice, List<Long> addonIds) {
        LocalDateTime pickup = daysFromNow(pickupDays);
        LocalDateTime dropoff = daysFromNow(dropoffDays);
        return """
                {
                  "vehicleId": %d,
                  "pickupLocation": "Airport Desk",
                  "pickupDate": "%s",
                  "pickupTime": "%s",
                  "returnLocation": "Airport Desk",
                  "returnDate": "%s",
                  "returnTime": "%s",
                  "firstName": "Office",
                  "lastName": "Customer",
                  "email": "office.customer.%d@example.com",
                  "phoneCountryCode": "+34",
                  "phoneNumber": "600000000",
                  "insurancePackageId": 1,
                  "addonIds": %s,
                  "totalPrice": %s,
                  "paymentSource": "%s",
                  "internalNote": "Desk reservation"
                }
                """.formatted(
                carId,
                pickup.toLocalDate(),
                pickup.toLocalTime(),
                dropoff.toLocalDate(),
                dropoff.toLocalTime(),
                pickupDays,
                addonIds.toString(),
                totalPrice,
                paymentSource);
    }

    private String adminBookingBodyWithLocations(long carId,
                                                 int pickupDays,
                                                 int dropoffDays,
                                                 String paymentSource,
                                                 String totalPrice,
                                                 String pickupLocation,
                                                 String pickupAddress,
                                                 String pickupPlaceId,
                                                 String returnLocation,
                                                 String returnAddress,
                                                 String returnPlaceId) {
        LocalDateTime pickup = daysFromNow(pickupDays);
        LocalDateTime dropoff = daysFromNow(dropoffDays);
        return """
                {
                  "vehicleId": %d,
                  "pickupLocation": "%s",
                  "pickupAddress": "%s",
                  "pickupPlaceId": "%s",
                  "pickupDate": "%s",
                  "pickupTime": "%s",
                  "returnLocation": "%s",
                  "returnAddress": "%s",
                  "returnPlaceId": "%s",
                  "returnDate": "%s",
                  "returnTime": "%s",
                  "firstName": "Office",
                  "lastName": "Customer",
                  "email": "office.customer.%d@example.com",
                  "phoneCountryCode": "+34",
                  "phoneNumber": "600000000",
                  "insurancePackageId": 1,
                  "addonIds": [],
                  "totalPrice": %s,
                  "paymentSource": "%s",
                  "internalNote": "Desk reservation"
                }
                """.formatted(
                carId,
                pickupLocation,
                pickupAddress,
                pickupPlaceId,
                pickup.toLocalDate(),
                pickup.toLocalTime(),
                returnLocation,
                returnAddress,
                returnPlaceId,
                dropoff.toLocalDate(),
                dropoff.toLocalTime(),
                pickupDays,
                totalPrice,
                paymentSource);
    }

    private long anyAvailableCarId(LocalDateTime pickup, LocalDateTime dropoff) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/cars/search")
                        .param("pickupDateTime", FMT.format(pickup))
                        .param("dropoffDateTime", FMT.format(dropoff)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).as("No available cars for test date range").isNotEmpty();
        return ids.get(0);
    }

    private static LocalDateTime daysFromNow(int days) {
        return LocalDateTime.now().plusDays(days).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }
}
