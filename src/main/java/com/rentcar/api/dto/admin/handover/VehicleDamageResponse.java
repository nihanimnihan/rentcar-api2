package com.rentcar.api.dto.admin.handover;

import com.rentcar.api.domain.handover.VehicleDamageSeverity;

import java.time.Instant;

public record VehicleDamageResponse(
        Long id,
        Long carId,
        String damageCode,
        String title,
        String description,
        String location,
        VehicleDamageSeverity severity,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
