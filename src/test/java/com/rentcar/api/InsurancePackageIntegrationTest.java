package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.insurance.InsurancePackage;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.InsurancePackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InsurancePackageIntegrationTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InsurancePackageRepository insurancePackageRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void activePackages_arePublicOrderedLocalizedAndIncludeCoverage() throws Exception {
        mockMvc.perform(get("/api/insurance-packages/active").param("lang", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("BASIC"))
                .andExpect(jsonPath("$[0].name").value("Protección básica"))
                .andExpect(jsonPath("$[0].coverageItems.length()").value(5))
                .andExpect(jsonPath("$[1].code").value("FULL"))
                .andExpect(jsonPath("$[1].recommended").value(true));
    }

    @Test
    void customerBooking_requiresInsurancePackage() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingBodyWithoutInsurance(3200, 3201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("insurancePackageId must not be null"));
    }

    @Test
    void customerBooking_rejectsInactiveInsurancePackage() throws Exception {
        InsurancePackage inactive = insurancePackageRepository.save(InsurancePackage.builder()
                .code("TEST_INACTIVE")
                .nameEn("Inactive")
                .nameEs("Inactivo")
                .nameTr("Pasif")
                .descriptionEn("Inactive package")
                .descriptionEs("Paquete inactivo")
                .descriptionTr("Pasif paket")
                .pricePerDay(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("500.00"))
                .displayOrder(99)
                .active(false)
                .recommended(false)
                .build());

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingBody(3202, 3203, inactive.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selected insurance package is not available."));
    }

    @Test
    void fullInsurance_isAddedToTotalAndSnapshotsAreImmutable() throws Exception {
        InsurancePackage full = insurancePackageRepository.findByCode("FULL").orElseThrow();
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingBody(3204, 3205, full.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insuranceCode").value("FULL"))
                .andExpect(jsonPath("$.insuranceNameSnapshot").value("Full Protection"))
                .andExpect(jsonPath("$.insuranceDailyPriceSnapshot").value(21.00))
                .andExpect(jsonPath("$.insuranceTotalSnapshot").value(21.00))
                .andExpect(jsonPath("$.depositAmountSnapshot").value(300.00))
                .andExpect(jsonPath("$.totalPrice").value(116.00))
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        full.setPricePerDay(new BigDecimal("99.00"));
        full.setDepositAmount(new BigDecimal("999.00"));
        insurancePackageRepository.saveAndFlush(full);

        var details = bookingRepository.findByIdWithDetails(bookingId).orElseThrow().getRentalDetails();
        assertThat(details.getInsuranceDailyPriceSnapshot()).isEqualByComparingTo("21.00");
        assertThat(details.getInsuranceTotalSnapshot()).isEqualByComparingTo("21.00");
        assertThat(details.getDepositAmountSnapshot()).isEqualByComparingTo("300.00");

        full.setPricePerDay(new BigDecimal("21.00"));
        full.setDepositAmount(new BigDecimal("300.00"));
        insurancePackageRepository.saveAndFlush(full);
    }

    @Test
    void adminBooking_requiresSnapshotsAndIncludesInsuranceTotal() throws Exception {
        InsurancePackage full = insurancePackageRepository.findByCode("FULL").orElseThrow();
        MvcResult created = mockMvc.perform(post("/api/admin/bookings")
                        .with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBookingBody(3210, 3212, full.getId(), "232.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.insuranceCode").value("FULL"))
                .andExpect(jsonPath("$.insuranceTotalSnapshot").value(42.00))
                .andExpect(jsonPath("$.depositAmountSnapshot").value(300.00))
                .andExpect(jsonPath("$.totalPrice").value(232.00))
                .andReturn();

        long bookingId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        mockMvc.perform(get("/api/admin/bookings/{id}", bookingId)
                        .with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insuranceCode").value("FULL"))
                .andExpect(jsonPath("$.insuranceTotalSnapshot").value(42.00));
    }

    private String customerBookingBody(int pickupDays, int dropoffDays, long insurancePackageId) {
        return customerBookingBodyWithoutInsurance(pickupDays, dropoffDays)
                .replace("\"dropoffLocation\": \"City Centre\"",
                        "\"dropoffLocation\": \"City Centre\",\n  \"insurancePackageId\": " + insurancePackageId);
    }

    private String customerBookingBodyWithoutInsurance(int pickupDays, int dropoffDays) {
        return """
                {
                  "carId": 1,
                  "customerName": "Insurance Test",
                  "customerEmail": "insurance-test@example.com",
                  "customerPhone": "+34600000999",
                  "pickupDateTime": "%s",
                  "dropoffDateTime": "%s",
                  "pickupLocation": "City Centre",
                  "dropoffLocation": "City Centre"
                }
                """.formatted(daysFromNow(pickupDays), daysFromNow(dropoffDays));
    }

    private String adminBookingBody(int pickupDays, int dropoffDays, long insurancePackageId, String totalPrice) {
        LocalDateTime pickup = LocalDateTime.now().plusDays(pickupDays).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime dropoff = LocalDateTime.now().plusDays(dropoffDays).withHour(10).withMinute(0).withSecond(0).withNano(0);
        return """
                {
                  "vehicleId": 1,
                  "pickupLocation": "City Centre",
                  "pickupDate": "%s",
                  "pickupTime": "%s",
                  "returnLocation": "City Centre",
                  "returnDate": "%s",
                  "returnTime": "%s",
                  "firstName": "Insurance",
                  "lastName": "Admin",
                  "email": "insurance-admin@example.com",
                  "phoneCountryCode": "+34",
                  "phoneNumber": "600000999",
                  "insurancePackageId": %d,
                  "addonIds": [],
                  "totalPrice": %s,
                  "paymentSource": "CASH",
                  "internalNote": "Insurance test"
                }
                """.formatted(
                pickup.toLocalDate(),
                pickup.toLocalTime(),
                dropoff.toLocalDate(),
                dropoff.toLocalTime(),
                insurancePackageId,
                totalPrice);
    }

    private String daysFromNow(int days) {
        return LocalDateTime.now()
                .plusDays(days)
                .withHour(10).withMinute(0).withSecond(0).withNano(0)
                .format(FMT);
    }
}
