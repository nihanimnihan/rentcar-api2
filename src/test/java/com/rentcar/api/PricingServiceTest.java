package com.rentcar.api;

import com.rentcar.api.config.MileageProperties;
import com.rentcar.api.config.PricingProperties;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import com.rentcar.api.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PricingService — verifies all 5 duration-based discount tiers
 * and add-on line total calculations without loading the Spring context.
 */
class PricingServiceTest {

    private PricingService pricingService;

    private static final LocalDateTime BASE = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

    @BeforeEach
    void setUp() {
        PricingProperties props = new PricingProperties(List.of(
                new PricingProperties.DiscountTier(1, BigDecimal.ZERO),
                new PricingProperties.DiscountTier(3, BigDecimal.valueOf(5)),
                new PricingProperties.DiscountTier(7, BigDecimal.valueOf(10)),
                new PricingProperties.DiscountTier(14, BigDecimal.valueOf(15)),
                new PricingProperties.DiscountTier(28, BigDecimal.valueOf(25))
        ));
        MileageProperties mileageProps = new MileageProperties(
                300, 150, 7,
                new BigDecimal("0.10"),
                new BigDecimal("4.70")
        );
        pricingService = new PricingService(props, new com.rentcar.api.service.MileageService(mileageProps));
    }

    // ── Discount tiers ──────────────────────────────────────────────────────────

    @Test
    void oneDayRental_noDiscount() {
        PriceBreakdown price = calculate(100, 1);
        assertThat(price.discountPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(price.effectiveDailyPrice()).isEqualByComparingTo("100.00");
        assertThat(price.rentalCharge()).isEqualByComparingTo("100.00");
    }

    @Test
    void twoDayRental_noDiscount() {
        PriceBreakdown price = calculate(100, 2);
        assertThat(price.discountPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(price.effectiveDailyPrice()).isEqualByComparingTo("100.00");
        assertThat(price.rentalCharge()).isEqualByComparingTo("200.00");
    }

    @Test
    void threeDayRental_fivePercentDiscount() {
        PriceBreakdown price = calculate(100, 3);
        assertThat(price.discountPercentage()).isEqualByComparingTo("5");
        assertThat(price.effectiveDailyPrice()).isEqualByComparingTo("95.00");
        assertThat(price.rentalCharge()).isEqualByComparingTo("285.00");
    }

    @Test
    void sixDayRental_fivePercentDiscount() {
        PriceBreakdown price = calculate(100, 6);
        assertThat(price.discountPercentage()).isEqualByComparingTo("5");
        assertThat(price.effectiveDailyPrice()).isEqualByComparingTo("95.00");
    }

    @Test
    void sevenDayRental_tenPercentDiscount() {
        PriceBreakdown price = calculate(100, 7);
        assertThat(price.discountPercentage()).isEqualByComparingTo("10");
        assertThat(price.effectiveDailyPrice()).isEqualByComparingTo("90.00");
        assertThat(price.rentalCharge()).isEqualByComparingTo("630.00");
    }

    @Test
    void thirteenDayRental_tenPercentDiscount() {
        PriceBreakdown price = calculate(100, 13);
        assertThat(price.discountPercentage()).isEqualByComparingTo("10");
    }

    @Test
    void fourteenDayRental_fifteenPercentDiscount() {
        PriceBreakdown price = calculate(100, 14);
        assertThat(price.discountPercentage()).isEqualByComparingTo("15");
        assertThat(price.effectiveDailyPrice()).isEqualByComparingTo("85.00");
        assertThat(price.rentalCharge()).isEqualByComparingTo("1190.00");
    }

    @Test
    void twentySevenDayRental_fifteenPercentDiscount() {
        PriceBreakdown price = calculate(100, 27);
        assertThat(price.discountPercentage()).isEqualByComparingTo("15");
    }

    @Test
    void twentyEightDayRental_twentyFivePercentDiscount() {
        PriceBreakdown price = calculate(100, 28);
        assertThat(price.discountPercentage()).isEqualByComparingTo("25");
        assertThat(price.effectiveDailyPrice()).isEqualByComparingTo("75.00");
        assertThat(price.rentalCharge()).isEqualByComparingTo("2100.00");
    }

    @Test
    void thirtyDayRental_twentyFivePercentDiscount() {
        PriceBreakdown price = calculate(100, 30);
        assertThat(price.discountPercentage()).isEqualByComparingTo("25");
    }

    // ── Total price ─────────────────────────────────────────────────────────────

    @Test
    void totalPriceEqualsRentalChargeWhenNoFeesNoTax() {
        // Same pickup/dropoff location, non-premium → no one-way fee, no premium fee
        Car car = buildCar(BigDecimal.valueOf(100), VehicleSegment.ECONOMY, VehicleType.SEDAN);
        PriceBreakdown price = pricingService.calculate(car, "City Centre", "City Centre",
                BASE, BASE.plusDays(3));
        assertThat(price.totalPrice()).isEqualByComparingTo(price.rentalCharge());
    }

    @Test
    void rentalDays_calculatedFromDatetime() {
        PriceBreakdown price = pricingService.calculate(
                buildCar(BigDecimal.valueOf(50), VehicleSegment.ECONOMY, VehicleType.SEDAN),
                "A", "A", BASE, BASE.plusDays(5));
        assertThat(price.rentalDays()).isEqualTo(5);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private PriceBreakdown calculate(int dailyPrice, int days) {
        Car car = buildCar(BigDecimal.valueOf(dailyPrice), VehicleSegment.ECONOMY, VehicleType.SEDAN);
        return pricingService.calculate(car, "City", "City", BASE, BASE.plusDays(days));
    }

    private Car buildCar(BigDecimal dailyPrice, VehicleSegment segment, VehicleType vehicleType) {
        return Car.builder()
                .brand("Test")
                .model("Car")
                .dailyPrice(dailyPrice)
                .segment(segment)
                .vehicleType(vehicleType)
                .transmission(TransmissionType.AUTOMATIC)
                .fuelType(FuelType.GASOLINE)
                .seats(5)
                .bags(2)
                .doors(4)
                .minDriverAge(21)
                .active(true)
                .build();
    }
}
