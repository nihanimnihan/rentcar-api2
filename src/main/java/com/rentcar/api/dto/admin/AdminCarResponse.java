package com.rentcar.api.dto.admin;

import java.math.BigDecimal;
import java.util.List;
import com.rentcar.api.dto.admin.handover.VehicleDamageResponse;

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
        BigDecimal hourlyPrice,
        List<VehicleDamageResponse> damages
) {
    public record CategoryRef(Long id, String code, String name) {}
}
