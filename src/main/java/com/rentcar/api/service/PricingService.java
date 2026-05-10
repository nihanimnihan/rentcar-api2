package com.rentcar.api.service;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import com.rentcar.api.dto.car.CarSearchRequest;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class PricingService {

    private static final BigDecimal TAX_RATE = BigDecimal.valueOf(0);
    private static final BigDecimal ONE_WAY_FEE = BigDecimal.valueOf(25);

    public PriceBreakdown calculate(Car car, CarSearchRequest request) {
        int rentalDays = calculateRentalDays(request.pickupDateTime(), request.dropoffDateTime());

        BigDecimal baseDailyPrice = money(car.getDailyPrice());
        BigDecimal discountedDailyPrice = money(baseDailyPrice.multiply(getDurationMultiplier(rentalDays)));

        BigDecimal rentalCharge = money(discountedDailyPrice.multiply(BigDecimal.valueOf(rentalDays)));

        BigDecimal oneWayFee = calculateOneWayFee(request.pickupLocation(), request.dropoffLocation());
        BigDecimal premiumLocationFee = calculatePremiumLocationFee(car, request.pickupLocation(), rentalCharge);

        BigDecimal subtotal = rentalCharge.add(oneWayFee).add(premiumLocationFee);

        BigDecimal tax = money(subtotal.multiply(TAX_RATE));
        BigDecimal totalPrice = money(subtotal.add(tax));
        return new PriceBreakdown(rentalDays, baseDailyPrice, discountedDailyPrice, rentalCharge, oneWayFee, premiumLocationFee, tax, totalPrice);
    }

    public PriceBreakdown calculate(Car car, String pickupLocation, String dropoffLocation, LocalDateTime pickupDateTime, LocalDateTime dropoffDateTime) {
        CarSearchRequest request = new CarSearchRequest(pickupLocation, dropoffLocation, pickupDateTime, dropoffDateTime, null, null, null, null, null, null, null);
        return calculate(car, request);
    }

    private int calculateRentalDays(LocalDateTime pickup, LocalDateTime dropoff) {
        if (pickup == null || dropoff == null || !dropoff.isAfter(pickup)) {
            return 1;
        }
        long hours = ChronoUnit.HOURS.between(pickup, dropoff);
        return Math.max(1, (int) Math.ceil(hours / 24.0));
    }

    private BigDecimal getDurationMultiplier(int rentalDays) {
        if (rentalDays >= 14) {
            return BigDecimal.valueOf(0.85);
        }
        if (rentalDays >= 7) {
            return BigDecimal.valueOf(0.90);
        }
        if (rentalDays >= 3) {
            return BigDecimal.valueOf(0.95);
        }

        return BigDecimal.ONE;
    }

    private BigDecimal calculateOneWayFee(String pickupLocation, String dropoffLocation) {
        if (pickupLocation == null || dropoffLocation == null) {
            return BigDecimal.ZERO;
        }
        if (pickupLocation.equalsIgnoreCase(dropoffLocation)) {
            return BigDecimal.ZERO;
        }
        return ONE_WAY_FEE;
    }

    private BigDecimal calculatePremiumLocationFee(Car car, String pickupLocation, BigDecimal rentalCharge) {
        if (!isPremiumLocation(pickupLocation)) {
            return BigDecimal.ZERO;
        }
        return money(rentalCharge.multiply(getPremiumLocationRate(car)));
    }

    private boolean isPremiumLocation(String location) {
        if (location == null) {
            return false;
        }
        String normalizedLocation = location.toLowerCase();
        return normalizedLocation.contains("airport") || normalizedLocation.contains("t1") || normalizedLocation.contains("t2");
    }

    private BigDecimal getPremiumLocationRate(Car car) {
        VehicleSegment segment = car.getSegment();
        VehicleType vehicleType = car.getVehicleType();

        if (segment == VehicleSegment.LUXURY || segment == VehicleSegment.PREMIUM) {
            return BigDecimal.valueOf(0.12);
        }
        if (vehicleType == VehicleType.SUV || vehicleType == VehicleType.VAN) {
            return BigDecimal.valueOf(0.10);
        }
        return BigDecimal.valueOf(0.07);
    }

    private BigDecimal money(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

    }
}