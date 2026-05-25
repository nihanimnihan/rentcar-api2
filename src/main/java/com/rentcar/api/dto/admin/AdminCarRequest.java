package com.rentcar.api.dto.admin;

import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminCarRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotNull VehicleSegment segment,
        @NotNull VehicleType vehicleType,
        @NotNull TransmissionType transmission,
        @NotNull FuelType fuelType,
        @NotNull @Min(1) Integer seats,
        @NotNull @Min(0) Integer bags,
        @NotNull @Min(2) Integer doors,
        @NotNull @Min(18) Integer minDriverAge,
        @NotNull Boolean airConditioning,
        @NotNull Boolean premium,
        @NotNull Boolean guaranteedModel,
        @NotNull @DecimalMin("0.01") BigDecimal dailyPrice,
        @NotNull Boolean active,
        @NotBlank String displayClass,
        String imageUrl,
        boolean chauffeurAvailable,
        Long chauffeurCategoryId,
        @DecimalMin("0.01") BigDecimal hourlyPrice
) {
}
