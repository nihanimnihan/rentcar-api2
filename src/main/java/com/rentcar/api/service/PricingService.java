package com.rentcar.api.service;

import com.rentcar.api.config.PricingProperties;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import com.rentcar.api.dto.car.CarSearchRequest;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PricingService {

    private static final BigDecimal TAX_RATE = BigDecimal.ZERO;
    private static final BigDecimal ONE_WAY_FEE = BigDecimal.valueOf(25);

    private final PricingProperties pricingProperties;
    private final MileageService mileageService;

    // Normalized (lowercased, trimmed) set of premium location names.
    // Built once at startup from properties — exact match, never substring.
    private Set<String> premiumLocationSet;

    @PostConstruct
    public void init() {
        premiumLocationSet = pricingProperties.normalizedPremiumLocations();
    }

    public PriceBreakdown calculate(Car car, CarSearchRequest request) {
        int rentalDays = calculateRentalDays(request.pickupDateTime(), request.dropoffDateTime());

        BigDecimal baseDailyPrice = money(car.getDailyPrice());
        BigDecimal discountPercentage = resolveDiscountPercentage(rentalDays);

        // multiplier = 1 - (discountPercentage / 100)
        BigDecimal multiplier = BigDecimal.ONE.subtract(
                discountPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        BigDecimal effectiveDailyPrice = money(baseDailyPrice.multiply(multiplier));
        BigDecimal rentalCharge = money(effectiveDailyPrice.multiply(BigDecimal.valueOf(rentalDays)));

        BigDecimal oneWayFee = calculateOneWayFee(request.pickupLocation(), request.dropoffLocation());
        BigDecimal premiumLocationFee = calculatePremiumLocationFee(car, request.pickupLocation(), rentalCharge);

        BigDecimal subtotal = rentalCharge.add(oneWayFee).add(premiumLocationFee);
        BigDecimal tax = money(subtotal.multiply(TAX_RATE));
        BigDecimal totalPrice = money(subtotal.add(tax));

        int includedKm = mileageService.calculateIncludedKm(rentalDays);
        BigDecimal unlimitedKmDailyPrice = mileageService.calculateUnlimitedKmDailyPrice(effectiveDailyPrice);

        return new PriceBreakdown(
                rentalDays,
                baseDailyPrice,
                effectiveDailyPrice,
                discountPercentage,
                rentalCharge,
                oneWayFee,
                premiumLocationFee,
                tax,
                totalPrice,
                null,
                null,
                includedKm,
                unlimitedKmDailyPrice
        );
    }

    public PriceBreakdown calculate(Car car, String pickupLocation, String dropoffLocation,
                                    LocalDateTime pickupDateTime, LocalDateTime dropoffDateTime) {
        CarSearchRequest request = new CarSearchRequest(
                pickupLocation, dropoffLocation, pickupDateTime, dropoffDateTime,
                null, null, null, null, null, null, null);
        return calculate(car, request);
    }

    /**
     * Finds the highest-threshold tier whose minDays <= rentalDays.
     * Tiers are configured in application properties — no hardcoded logic here.
     */
    private BigDecimal resolveDiscountPercentage(int rentalDays) {
        return pricingProperties.discountTiers().stream()
                .filter(tier -> rentalDays >= tier.minDays())
                .max(Comparator.comparingInt(PricingProperties.DiscountTier::minDays))
                .map(PricingProperties.DiscountTier::discountPercentage)
                .orElse(BigDecimal.ZERO);
    }

    private int calculateRentalDays(LocalDateTime pickup, LocalDateTime dropoff) {
        if (pickup == null || dropoff == null || !dropoff.isAfter(pickup)) {
            return 1;
        }
        long hours = ChronoUnit.HOURS.between(pickup, dropoff);
        return Math.max(1, (int) Math.ceil(hours / 24.0));
    }

    private BigDecimal calculateOneWayFee(String pickupLocation, String dropoffLocation) {
        if (pickupLocation == null || dropoffLocation == null) {
            return BigDecimal.ZERO;
        }
        return pickupLocation.equalsIgnoreCase(dropoffLocation) ? BigDecimal.ZERO : ONE_WAY_FEE;
    }

    private BigDecimal calculatePremiumLocationFee(Car car, String pickupLocation, BigDecimal rentalCharge) {
        if (!isPremiumLocation(pickupLocation)) {
            return BigDecimal.ZERO;
        }
        return money(rentalCharge.multiply(getPremiumLocationRate(car)));
    }

    private boolean isPremiumLocation(String location) {
        if (location == null) return false;
        return premiumLocationSet.contains(location.trim().toLowerCase());
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
