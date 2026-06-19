package com.rentcar.api.dto.car;

import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarSearchRequest {

    private String pickupLocation;
    private String dropoffLocation;

    private String dropoffAddress;
    private String dropoffPlaceId;

    private String pickupAddress;
    private String pickupPlaceId;

    private LocalDateTime pickupDateTime;
    private LocalDateTime dropoffDateTime;

    private VehicleType vehicleType;
    private VehicleSegment segment;
    private TransmissionType transmission;
    private FuelType fuelType;

    private Integer minSeats;
    private Integer minBags;
    private Integer minDriverAge;

    private Boolean premium;
    private Boolean guaranteedModel;

    public CarSearchRequest(String pickupLocation, String dropoffLocation, LocalDateTime pickupDateTime, LocalDateTime dropoffDateTime) {
        this.pickupLocation = pickupLocation;
        this.dropoffLocation = dropoffLocation;
        this.pickupDateTime = pickupDateTime;
        this.dropoffDateTime = dropoffDateTime;
    }
}
