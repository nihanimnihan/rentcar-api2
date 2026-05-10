package com.rentcar.api.dto.car;

import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;

import java.time.LocalDateTime;

public record CarSearchRequest(
        String pickupLocation,
        String dropoffLocation,
        LocalDateTime pickupDateTime,
        LocalDateTime dropoffDateTime,

        VehicleType vehicleType,
        VehicleSegment segment,
        TransmissionType transmission,
        FuelType fuelType,

        Integer minSeats,
        Integer minBags,
        Integer minDriverAge
) {
}