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
            if (carRepository.count() == 0) {
                carRepository.save(Car.builder()
                        .brand("BMW")
                        .model("320i")
                        .segment("SEDAN")
                        .dailyPrice(new BigDecimal("95.00"))
                        .active(true)
                        .imageUrl("https://images.unsplash.com/photo-1555215695-3004980ad54e")
                        .build());

                carRepository.save(Car.builder()
                        .brand("Mercedes")
                        .model("Vito")
                        .segment("VAN")
                        .dailyPrice(new BigDecimal("140.00"))
                        .active(true)
                        .imageUrl("https://images.unsplash.com/photo-1555215695-3004980ad54e")
                        .build());

                carRepository.save(Car.builder()
                        .brand("Audi")
                        .model("A6")
                        .segment("VIP")
                        .dailyPrice(new BigDecimal("180.00"))
                        .active(true)
                        .imageUrl("https://images.unsplash.com/photo-1555215695-3004980ad54e")
                        .build());
            }
        };
    }
}