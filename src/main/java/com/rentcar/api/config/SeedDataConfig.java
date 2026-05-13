package com.rentcar.api.config;

import com.rentcar.api.domain.addon.Addon;
import com.rentcar.api.domain.addon.AddonPricingType;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import com.rentcar.api.repository.AddonRepository;
import com.rentcar.api.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SeedDataConfig {

    private final CarRepository carRepository;
    private final AddonRepository addonRepository;

    @Bean
    CommandLineRunner seedCars() {
        return args -> {

            if (carRepository.count() > 0) {
                return;
            }

            carRepository.save(
                    Car.builder()
                            .brand("BMW sDrive18d")
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
                            .bags(3)
                            .airConditioning(true)
                            .premium(true)
                            .guaranteedModel(true)
                            .dailyPrice(new BigDecimal("180.00"))
                            .active(true)
                            .imageUrl("img/cars/audi_q2.png")
                            .displayClass("Compact Elite")
                            .doors(5)
                            .minDriverAge(21)
                            .build()
            );
        };
    }

    @Bean
    CommandLineRunner seedAddons() {
        return args -> {
            if (addonRepository.count() > 0) {
                return;
            }

            addonRepository.saveAll(List.of(
                    Addon.builder()
                            .name("Additional Driver")
                            .description("Allow an extra driver on the rental")
                            .price(new BigDecimal("19.00"))
                            .pricingType(AddonPricingType.DAILY)
                            .imageUrl("img/addons/additional-driver.jpg")
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("Child Seat")
                            .description("Rear-facing, forward-facing, and booster seats available")
                            .price(new BigDecimal("15.00"))
                            .pricingType(AddonPricingType.DAILY)
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("GPS Navigation")
                            .description("Portable GPS device for your trip")
                            .price(new BigDecimal("12.00"))
                            .pricingType(AddonPricingType.DAILY)
                            .imageUrl("img/addons/gps.jpg")
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("Full Insurance")
                            .description("Full coverage with zero excess")
                            .price(new BigDecimal("25.00"))
                            .pricingType(AddonPricingType.DAILY)
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("Snow Chains")
                            .description("For winter driving in mountain areas")
                            .price(new BigDecimal("30.00"))
                            .pricingType(AddonPricingType.ONE_TIME)
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("Refueling Service")
                            .description("Return the car without refueling — we handle it for you")
                            .price(new BigDecimal("35.00"))
                            .pricingType(AddonPricingType.ONE_TIME)
                            .imageUrl("img/addons/refueling.jpg")
                            .active(true)
                            .build()
            ));
        };
    }
}