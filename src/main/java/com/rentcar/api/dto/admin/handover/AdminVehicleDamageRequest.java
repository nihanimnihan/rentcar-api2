package com.rentcar.api.dto.admin.handover;

import com.rentcar.api.domain.handover.VehicleDamageSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminVehicleDamageRequest(
        @NotBlank String damageCode,
        @NotBlank String title,
        String description,
        String location,
        @NotNull VehicleDamageSeverity severity,
        boolean active
) {}
