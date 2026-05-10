package com.rentcar.api.config;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import com.rentcar.api.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@RequiredArgsConstructor
public class SeedDataConfig {

    private final CarRepository carRepository;

    @Bean
    CommandLineRunner seedCars() {
        return args -> {

            if (carRepository.count() > 0) {
                return;
            }

            carRepository.save(
                    Car.builder()
                            .brand("BMV sDrive18d")
                            .model("X1")
                            .segment(VehicleSegment.PREMIUM)
                            .vehicleType(VehicleType.SUV)
                            .transmission(TransmissionType.AUTOMATIC)
                            .fuelType(FuelType.HYBRID)
                            .seats(5)
                            .bags(3)
                            .airConditioning(true)
                            .premium(true)
                            .guaranteedModel(true)
                            .dailyPrice(new BigDecimal("95.00"))
                            .active(true)
                            .imageUrl("img/cars/bmw_x1_sdrive.png")
                            .displayClass("Compact Elite")
                            .doors(5)
                            .minDriverAge(21)
                            .build()
            );

            carRepository.save(
                    Car.builder()
                            .brand("Mercedes")
                            .model("Vito")
                            .segment(VehicleSegment.PREMIUM)
                            .vehicleType(VehicleType.VAN)
                            .transmission(TransmissionType.AUTOMATIC)
                            .fuelType(FuelType.DIESEL)
                            .seats(7)
                            .bags(5)
                            .airConditioning(true)
                            .premium(false)
                            .guaranteedModel(false)
                            .dailyPrice(new BigDecimal("140.00"))
                            .active(true)
                            .imageUrl("img/cars/mercedes_vito.png")
                            .displayClass("Compact Elite")
                            .doors(5)
                            .minDriverAge(26)
                            .build()
            );

            carRepository.save(
                    Car.builder()
                            .brand("Audi")
                            .model("Q2")
                            .segment(VehicleSegment.LUXURY)
                            .vehicleType(VehicleType.SEDAN)
                            .transmission(TransmissionType.AUTOMATIC)
                            .fuelType(FuelType.HYBRID)
                            .seats(5)
                            .bags(5)
                            .airConditioning(true)
                            .premium(true)
                            .guaranteedModel(true)
                            .dailyPrice(new BigDecimal("180.00"))
                            .active(true)
                            .imageUrl("img/cars/audi_q2.png")
                            .displayClass("Compact Elite")
                            .doors(3)
                            .minDriverAge(21)
                            .build()
            );
        };
    }
}