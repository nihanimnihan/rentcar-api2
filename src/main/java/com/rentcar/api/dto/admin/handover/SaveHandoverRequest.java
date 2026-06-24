package com.rentcar.api.dto.admin.handover;

import com.rentcar.api.domain.handover.BatteryLevel;
import com.rentcar.api.domain.handover.FuelLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveHandoverRequest(
        @NotNull @Min(0) Integer kmOut,
        @NotNull FuelLevel fuelLevelOut,
        @NotNull BatteryLevel batteryLevelOut,
        String customerSignatureData,
        String notes
) {}
