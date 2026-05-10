package com.rentcar.api.dto.car;

import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CarDetailResponse {
    private Long id;
    private String brand;
    private String model;
    private VehicleSegment segment;
    private VehicleType vehicleType;
    private TransmissionType transmission;
    private FuelType fuelType;
    private Integer seats;
    private Integer bags;
    private Integer doors;
    private Integer minDriverAge;
    private String imageUrl;
    private BigDecimal dailyPrice;
    private BigDecimal totalPrice;
    private PriceBreakdown priceBreakdown;
}