package com.rentcar.api.dto.admin.handover;

import com.rentcar.api.domain.handover.BatteryLevel;
import com.rentcar.api.domain.handover.FuelLevel;

import java.time.Instant;

public record VehicleHandoverResponse(
        Long id,
        Long bookingId,
        Instant handoverAt,
        Integer kmOut,
        FuelLevel fuelLevelOut,
        BatteryLevel batteryLevelOut,
        boolean customerSignaturePresent,
        Instant customerSignatureAt,
        Long depositId,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
