package com.rentcar.api.dto.car;

import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CarResponse {

    private Long id;

    private String brand;
    private String model;

    private String imageUrl;
    private String displayClass;

    private Integer seats;
    private Integer bags;
    private Integer doors;
    private Integer minDriverAge;
    private Boolean airConditioning;

    private VehicleSegment segment;
    private VehicleType vehicleType;
    private TransmissionType transmission;
    private FuelType fuelType;

    private BigDecimal dailyPrice;
    private BigDecimal totalPrice;
}