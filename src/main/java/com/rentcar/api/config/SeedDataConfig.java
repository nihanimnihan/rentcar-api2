package com.rentcar.api.config;

import com.rentcar.api.domain.car.Car;
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
                            .brand("BMW")
                            .model("320i")
                            .segment("PREMIUM")
                            .vehicleType("SEDAN")
                            .transmission("AUTOMATIC")
                            .fuelType("GASOLINE")
                            .seats(5)
                            .bags(3)
                            .airConditioning(true)
                            .premium(true)
                            .guaranteedModel(true)
                            .dailyPrice(new BigDecimal("95.00"))
                            .active(true)
                            .imageUrl("https://images.unsplash.com/photo-1555215695-3004980ad54e")
                            .displayClass("Compact Elite")
                            .doors(4)
                            .minDriverAge(21)
                            .build()
            );

            carRepository.save(
                    Car.builder()
                            .brand("Mercedes")
                            .model("Vito")
                            .segment("FAMILY")
                            .vehicleType("VAN")
                            .transmission("AUTOMATIC")
                            .fuelType("DIESEL")
                            .seats(7)
                            .bags(5)
                            .airConditioning(true)
                            .premium(false)
                            .guaranteedModel(false)
                            .dailyPrice(new BigDecimal("140.00"))
                            .active(true)
                            .imageUrl("https://images.unsplash.com/photo-1555215695-3004980ad54e")
                            .displayClass("Compact Elite")
                            .doors(4)
                            .minDriverAge(21)
                            .build()
            );

            carRepository.save(
                    Car.builder()
                            .brand("Audi")
                            .model("A6")
                            .segment("LUXURY")
                            .vehicleType("SEDAN")
                            .transmission("AUTOMATIC")
                            .fuelType("HYBRID")
                            .seats(5)
                            .bags(4)
                            .airConditioning(true)
                            .premium(true)
                            .guaranteedModel(true)
                            .dailyPrice(new BigDecimal("180.00"))
                            .active(true)
                            .imageUrl("https://images.unsplash.com/photo-1555215695-3004980ad54e")
                            .displayClass("Compact Elite")
                            .doors(4)
                            .minDriverAge(21)
                            .build()
            );
        };
    }
}