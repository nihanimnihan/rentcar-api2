package com.rentcar.api.dto.car;

import java.math.BigDecimal;

public record CarResponse(
        Long id,
        String brand,
        String model,
        String segment,
        String vehicleType,
        String transmission,
        String fuelType,
        Integer seats,
        Integer bags,
        Boolean airConditioning,
        Boolean premium,
        Boolean guaranteedModel,
        BigDecimal dailyPrice,
        String imageUrl
) {
}