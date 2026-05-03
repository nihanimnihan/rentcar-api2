package com.rentcar.api.dto.car;

import java.time.LocalDateTime;

public record CarSearchRequest(
        String pickupLocation,
        String dropoffLocation,
        LocalDateTime pickupDateTime,
        LocalDateTime dropoffDateTime,
        String vehicleType,
        String transmission,
        String fuelType,
        Integer seats,
        Integer bags,
        Boolean airConditioning,
        Boolean premium,
        Boolean guaranteedModel
) {
}
