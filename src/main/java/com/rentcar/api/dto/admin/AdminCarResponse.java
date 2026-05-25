package com.rentcar.api.dto.admin;

import java.math.BigDecimal;

public record AdminCarResponse(
        Long id,
        String brand,
        String model,
        String segment,
        String vehicleType,
        String transmission,
        String fuelType,
        Integer seats,
        Integer bags,
        Integer doors,
        Integer minDriverAge,
        Boolean airConditioning,
        Boolean premium,
        Boolean guaranteedModel,
        BigDecimal dailyPrice,
        Boolean active,
        String displayClass,
        String imageUrl,
        boolean chauffeurAvailable,
        CategoryRef chauffeurCategory,
        BigDecimal hourlyPrice
) {
    public record CategoryRef(Long id, String code, String name) {}
}
