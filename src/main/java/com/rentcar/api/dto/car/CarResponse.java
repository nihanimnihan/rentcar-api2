package com.rentcar.api.dto.car;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CarResponse {

    private Long id;

    private String brand;
    private String model;

    private String imageUrl;

    private Integer seats;
    private Integer bags;
    private Integer doors;
    private Integer minDriverAge;

    private Object segment;
    private Object vehicleType;
    private Object transmission;
    private Object fuelType;

    private BigDecimal dailyPrice;
    private BigDecimal totalPrice;
}