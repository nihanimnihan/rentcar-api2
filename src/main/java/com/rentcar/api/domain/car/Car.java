package com.rentcar.api.domain.car;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "cars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    @Enumerated(EnumType.STRING)
    private VehicleSegment segment;

    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    private TransmissionType transmission;

    @Enumerated(EnumType.STRING)
    private FuelType fuelType;

    @Column(nullable = false)
    private Integer seats;

    @Column(nullable = false)
    private Integer bags;

    @Column(nullable = false)
    private Boolean airConditioning;

    @Column(nullable = false)
    private Boolean premium;

    @Column(nullable = false)
    private Boolean guaranteedModel;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyPrice;

    @Column(nullable = false)
    private Boolean active;

    @Column
    private String imageUrl;

    @Column(nullable = false)
    private String displayClass;

    @Column(nullable = false)
    private Integer doors;

    @Column(nullable = false)
    private Integer minDriverAge;
}