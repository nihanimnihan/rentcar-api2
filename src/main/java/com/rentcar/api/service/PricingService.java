package com.rentcar.api.service;

import com.rentcar.api.domain.car.Car;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class PricingService {

    public BigDecimal calculateTotalPrice(
            Car car,
            LocalDateTime pickupDateTime,
            LocalDateTime dropoffDateTime
    ) {
        long hours = ChronoUnit.HOURS.between(pickupDateTime, dropoffDateTime);

        long chargeableDays = Math.max(1, (long) Math.ceil(hours / 24.0));

        return car.getDailyPrice()
                .multiply(BigDecimal.valueOf(chargeableDays));
    }
}