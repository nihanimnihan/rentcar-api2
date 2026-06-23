package com.rentcar.api.config;

import com.rentcar.api.domain.addon.Addon;
import com.rentcar.api.domain.addon.AddonPricingType;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import com.rentcar.api.domain.insurance.InsuranceCoverageItem;
import com.rentcar.api.domain.insurance.InsurancePackage;
import com.rentcar.api.domain.transfer.ChauffeurCategory;
import com.rentcar.api.domain.transfer.TransferDuration;
import com.rentcar.api.repository.AddonRepository;
import com.rentcar.api.repository.CarRepository;
import com.rentcar.api.repository.ChauffeurCategoryRepository;
import com.rentcar.api.repository.InsurancePackageRepository;
import com.rentcar.api.repository.TransferDurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class SeedDataConfig {

    private final CarRepository carRepository;
    private final AddonRepository addonRepository;
    private final InsurancePackageRepository insurancePackageRepository;
    private final TransferDurationRepository transferDurationRepository;
    private final ChauffeurCategoryRepository chauffeurCategoryRepository;

    @Bean
    @Order(1)
    CommandLineRunner seedChauffeurCategories() {
        return args -> {
            if (chauffeurCategoryRepository.count() > 0) {
                return;
            }

            chauffeurCategoryRepository.saveAll(List.of(
                    ChauffeurCategory.builder()
                            .code("RIDE").name("Ride")
                            .description("Reliable ride at the best price.")
                            .seats(3).bags(2).electric(false).active(true).displayOrder(1).build(),
                    ChauffeurCategory.builder()
                            .code("GREEN").name("Green")
                            .description("Ride in hybrid or electric vehicles.")
                            .seats(3).bags(2).electric(true).active(true).displayOrder(2).build(),
                    ChauffeurCategory.builder()
                            .code("FIRST").name("First")
                            .description("Luxury chauffeur service with maximum comfort.")
                            .seats(3).bags(2).electric(false).active(true).displayOrder(3).build(),
                    ChauffeurCategory.builder()
                            .code("BUSINESS").name("Business")
                            .description("Chauffeur service in limousines.")
                            .seats(3).bags(2).electric(false).active(true).displayOrder(4).build(),
                    ChauffeurCategory.builder()
                            .code("RIDE_XL").name("Ride XL")
                            .description("Ride in spacious vans for group travels.")
                            .seats(6).bags(4).electric(false).active(true).displayOrder(5).build(),
                    ChauffeurCategory.builder()
                            .code("BUSINESS_GREEN").name("Business Green")
                            .description("Chauffeur service fully electric limousines.")
                            .seats(3).bags(2).electric(true).active(true).displayOrder(6).build(),
                    ChauffeurCategory.builder()
                            .code("BUSINESS_XL").name("Business XL")
                            .description("Chauffeur service in spacious premium vans.")
                            .seats(6).bags(4).electric(false).active(true).displayOrder(7).build()
            ));
        };
    }

    @Bean
    @Order(2)
    CommandLineRunner seedCars() {
        return args -> {
            if (carRepository.count() > 0) {
                return;
            }

            ChauffeurCategory ride = chauffeurCategoryRepository.findByCode("RIDE").orElseThrow();
            ChauffeurCategory green = chauffeurCategoryRepository.findByCode("GREEN").orElseThrow();
            ChauffeurCategory first = chauffeurCategoryRepository.findByCode("FIRST").orElseThrow();
            ChauffeurCategory business = chauffeurCategoryRepository.findByCode("BUSINESS").orElseThrow();
            ChauffeurCategory rideXl = chauffeurCategoryRepository.findByCode("RIDE_XL").orElseThrow();

            // ── Rental + chauffeur dual-purpose cars ──────────────────────────────
            carRepository.save(
                    Car.builder()
                            .brand("BMW sDrive18d").model("X1")
                            .segment(VehicleSegment.PREMIUM).vehicleType(VehicleType.SUV)
                            .transmission(TransmissionType.AUTOMATIC).fuelType(FuelType.HYBRID)
                            .seats(5).bags(3).airConditioning(true).premium(true).guaranteedModel(true)
                            .dailyPrice(new BigDecimal("95.00")).active(true)
                            .imageUrl("img/cars/bmw_x1_sdrive.png").displayClass("Compact Elite")
                            .doors(5).minDriverAge(21)
                            .chauffeurAvailable(true).chauffeurCategory(ride)
                            .hourlyPrice(new BigDecimal("95.00"))
                            .build()
            );

            carRepository.save(
                    Car.builder()
                            .brand("Mercedes").model("Vito")
                            .segment(VehicleSegment.PREMIUM).vehicleType(VehicleType.VAN)
                            .transmission(TransmissionType.AUTOMATIC).fuelType(FuelType.DIESEL)
                            .seats(7).bags(5).airConditioning(true).premium(false).guaranteedModel(false)
                            .dailyPrice(new BigDecimal("140.00")).active(true)
                            .imageUrl("img/cars/mercedes_vito.png").displayClass("Compact Elite")
                            .doors(5).minDriverAge(26)
                            .chauffeurAvailable(true).chauffeurCategory(rideXl)
                            .hourlyPrice(new BigDecimal("140.00"))
                            .build()
            );

            carRepository.save(
                    Car.builder()
                            .brand("Audi").model("Q2")
                            .segment(VehicleSegment.LUXURY).vehicleType(VehicleType.SEDAN)
                            .transmission(TransmissionType.AUTOMATIC).fuelType(FuelType.HYBRID)
                            .seats(5).bags(3).airConditioning(true).premium(true).guaranteedModel(true)
                            .dailyPrice(new BigDecimal("180.00")).active(true)
                            .imageUrl("img/cars/audi_q2.png").displayClass("Compact Elite")
                            .doors(5).minDriverAge(21)
                            .chauffeurAvailable(true).chauffeurCategory(green)
                            .hourlyPrice(new BigDecimal("120.00"))
                            .build()
            );

            // ── Chauffeur-only premium cars ───────────────────────────────────────
            carRepository.save(
                    Car.builder()
                            .brand("Mercedes").model("E-Class")
                            .segment(VehicleSegment.LUXURY).vehicleType(VehicleType.SEDAN)
                            .transmission(TransmissionType.AUTOMATIC).fuelType(FuelType.DIESEL)
                            .seats(4).bags(3).airConditioning(true).premium(true).guaranteedModel(true)
                            .dailyPrice(new BigDecimal("200.00")).active(true)
                            .displayClass("Executive").doors(4).minDriverAge(25)
                            .chauffeurAvailable(true).chauffeurCategory(business)
                            .hourlyPrice(new BigDecimal("180.00"))
                            .build()
            );

            carRepository.save(
                    Car.builder()
                            .brand("BMW").model("7-Series")
                            .segment(VehicleSegment.LUXURY).vehicleType(VehicleType.SEDAN)
                            .transmission(TransmissionType.AUTOMATIC).fuelType(FuelType.HYBRID)
                            .seats(4).bags(3).airConditioning(true).premium(true).guaranteedModel(true)
                            .dailyPrice(new BigDecimal("300.00")).active(true)
                            .displayClass("Executive").doors(4).minDriverAge(25)
                            .chauffeurAvailable(true).chauffeurCategory(first)
                            .hourlyPrice(new BigDecimal("250.00"))
                            .build()
            );
        };
    }

    @Bean
    @Order(3)
    CommandLineRunner seedAddons() {
        return args -> {
            if (addonRepository.count() > 0) {
                return;
            }

            addonRepository.saveAll(List.of(
                    Addon.builder()
                            .name("Additional Driver")
                            .nameEs("Conductor Adicional")
                            .code("ADDITIONAL_DRIVER")
                            .description("Allow an extra driver on the rental")
                            .descriptionEs("Permite un conductor extra en el alquiler")
                            .price(new BigDecimal("19.00"))
                            .pricingType(AddonPricingType.DAILY)
                            .imageUrl("img/addons/additional-driver.jpg")
                            .recommended(true)
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("Child Seat")
                            .nameEs("Silla Infantil")
                            .code("CHILD_SEAT")
                            .description("Rear-facing, forward-facing, and booster seats available")
                            .descriptionEs("Disponibles sillas orientadas hacia atrás, hacia adelante y elevadores")
                            .price(new BigDecimal("15.00"))
                            .pricingType(AddonPricingType.DAILY)
                            .recommended(false)
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("GPS Navigation")
                            .nameEs("Navegador GPS")
                            .code("GPS_NAVIGATION")
                            .description("Portable GPS device for your trip")
                            .descriptionEs("Dispositivo GPS portátil para tu viaje")
                            .price(new BigDecimal("12.00"))
                            .pricingType(AddonPricingType.DAILY)
                            .imageUrl("img/addons/gps.jpg")
                            .recommended(true)
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("Full Insurance")
                            .nameEs("Seguro Completo")
                            .code("FULL_INSURANCE")
                            .description("Full coverage with zero excess")
                            .descriptionEs("Cobertura total con franquicia cero")
                            .price(new BigDecimal("25.00"))
                            .pricingType(AddonPricingType.DAILY)
                            .recommended(false)
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("Snow Chains")
                            .nameEs("Cadenas de Nieve")
                            .code("SNOW_CHAINS")
                            .description("For winter driving in mountain areas")
                            .descriptionEs("Para conducción invernal en zonas de montaña")
                            .price(new BigDecimal("30.00"))
                            .pricingType(AddonPricingType.ONE_TIME)
                            .recommended(false)
                            .active(true)
                            .build(),
                    Addon.builder()
                            .name("Refueling Service")
                            .nameEs("Servicio de Repostaje")
                            .code("REFUELING_SERVICE")
                            .description("Return the car without refueling — we handle it for you")
                            .descriptionEs("Devuelve el coche sin repostar — nosotros nos encargamos")
                            .price(new BigDecimal("35.00"))
                            .pricingType(AddonPricingType.ONE_TIME)
                            .imageUrl("img/addons/refueling.jpg")
                            .recommended(true)
                            .active(true)
                            .build()
            ));
        };
    }

    @Bean
    @Order(4)
    CommandLineRunner seedInsurancePackages() {
        return args -> {
            if (insurancePackageRepository.count() > 0) {
                return;
            }

            InsurancePackage basic = InsurancePackage.builder()
                    .code("BASIC")
                    .nameEn("Basic Protection")
                    .nameEs("Protección básica")
                    .nameTr("Temel Koruma")
                    .descriptionEn("Included protection with a higher refundable deposit.")
                    .descriptionEs("Protección incluida con un depósito reembolsable más alto.")
                    .descriptionTr("Daha yüksek iade edilebilir depozito ile dahil koruma.")
                    .pricePerDay(new BigDecimal("0.00"))
                    .depositAmount(new BigDecimal("750.00"))
                    .displayOrder(1)
                    .active(true)
                    .recommended(false)
                    .badgeEn("Included")
                    .badgeEs("Incluido")
                    .badgeTr("Dahil")
                    .build();
            addCoverage(basic, "Collision damage waiver", "Standard collision damage coverage with excess.",
                    "Cobertura por daños de colisión", "Cobertura estándar por daños de colisión con franquicia.",
                    "Çarpışma hasarı muafiyeti", "Muafiyetli standart çarpışma hasarı koruması.", true, 1);
            addCoverage(basic, "Theft protection", "Protection if the vehicle is stolen during the rental.",
                    "Protección contra robo", "Protección si el vehículo es robado durante el alquiler.",
                    "Hırsızlık koruması", "Kiralama sırasında aracın çalınmasına karşı koruma.", true, 2);
            addCoverage(basic, "Roadside assistance", "24/7 support for breakdowns and urgent issues.",
                    "Asistencia en carretera", "Soporte 24/7 para averías e incidencias urgentes.",
                    "Yol yardımı", "Arıza ve acil durumlar için 7/24 destek.", true, 3);
            addCoverage(basic, "Glass and tire damage", "Glass, tire, and underbody damage are not reduced.",
                    "Daños en cristales y neumáticos", "Los daños en cristales, neumáticos y bajos no se reducen.",
                    "Cam ve lastik hasarı", "Cam, lastik ve alt gövde hasarlarında indirim yoktur.", false, 4);
            addCoverage(basic, "Reduced deposit", "Standard deposit applies at pickup.",
                    "Depósito reducido", "Se aplica el depósito estándar en la recogida.",
                    "Azaltılmış depozito", "Teslim alma sırasında standart depozito uygulanır.", false, 5);

            InsurancePackage full = InsurancePackage.builder()
                    .code("FULL")
                    .nameEn("Full Protection")
                    .nameEs("Protección completa")
                    .nameTr("Tam Koruma")
                    .descriptionEn("Enhanced protection with lower deposit and broader coverage.")
                    .descriptionEs("Protección ampliada con menor depósito y cobertura más amplia.")
                    .descriptionTr("Daha düşük depozito ve genişletilmiş kapsamla gelişmiş koruma.")
                    .pricePerDay(new BigDecimal("21.00"))
                    .depositAmount(new BigDecimal("300.00"))
                    .displayOrder(2)
                    .active(true)
                    .recommended(true)
                    .badgeEn("Recommended")
                    .badgeEs("Recomendado")
                    .badgeTr("Önerilir")
                    .build();
            addCoverage(full, "Collision damage waiver", "Enhanced collision damage coverage with lower excess.",
                    "Cobertura por daños de colisión", "Cobertura ampliada por daños de colisión con menor franquicia.",
                    "Çarpışma hasarı muafiyeti", "Daha düşük muafiyetli geliştirilmiş çarpışma hasarı koruması.", true, 1);
            addCoverage(full, "Theft protection", "Protection if the vehicle is stolen during the rental.",
                    "Protección contra robo", "Protección si el vehículo es robado durante el alquiler.",
                    "Hırsızlık koruması", "Kiralama sırasında aracın çalınmasına karşı koruma.", true, 2);
            addCoverage(full, "Roadside assistance", "24/7 support for breakdowns and urgent issues.",
                    "Asistencia en carretera", "Soporte 24/7 para averías e incidencias urgentes.",
                    "Yol yardımı", "Arıza ve acil durumlar için 7/24 destek.", true, 3);
            addCoverage(full, "Glass and tire damage", "Glass, tire, and underbody damage are included.",
                    "Daños en cristales y neumáticos", "Cristales, neumáticos y bajos están incluidos.",
                    "Cam ve lastik hasarı", "Cam, lastik ve alt gövde hasarları dahildir.", true, 4);
            addCoverage(full, "Reduced deposit", "Lower refundable deposit at pickup.",
                    "Depósito reducido", "Depósito reembolsable más bajo en la recogida.",
                    "Azaltılmış depozito", "Teslim alma sırasında daha düşük iade edilebilir depozito.", true, 5);

            insurancePackageRepository.saveAll(List.of(basic, full));
        };
    }

    @Bean
    @Order(5)
    CommandLineRunner seedTransferDurations() {
        return args -> {
            if (transferDurationRepository.count() > 0) {
                return;
            }

            for (int hours = 1; hours <= 12; hours++) {
                transferDurationRepository.save(
                        TransferDuration.builder()
                                .hours(hours)
                                .includedKm(hours * 30)
                                .active(true)
                                .displayOrder(hours)
                                .build()
                );
            }
        };
    }

    private void addCoverage(
            InsurancePackage insurancePackage,
            String titleEn,
            String descriptionEn,
            String titleEs,
            String descriptionEs,
            String titleTr,
            String descriptionTr,
            boolean included,
            int displayOrder) {
        insurancePackage.addCoverageItem(InsuranceCoverageItem.builder()
                .titleEn(titleEn)
                .descriptionEn(descriptionEn)
                .titleEs(titleEs)
                .descriptionEs(descriptionEs)
                .titleTr(titleTr)
                .descriptionTr(descriptionTr)
                .included(included)
                .displayOrder(displayOrder)
                .build());
    }
}
