package com.rentcar.api.service;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.transfer.ChauffeurCategory;
import com.rentcar.api.dto.transfer.ChauffeurCategoryOfferResponse;
import com.rentcar.api.repository.CarRepository;
import com.rentcar.api.repository.ChauffeurCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TransferOfferService {

    private final ChauffeurCategoryRepository chauffeurCategoryRepository;
    private final CarRepository carRepository;
    private final com.rentcar.api.util.AppClock appClock;

    public List<ChauffeurCategoryOfferResponse> getOffers(
            LocalDateTime pickupDateTime, Integer durationHours, Integer passengers) {
        LocalDateTime dropoffDateTime = pickupDateTime.plusHours(durationHours);

        return chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .filter(category -> passengers == null || category.getSeats() >= passengers)
                .map(category -> buildOffer(category, pickupDateTime, dropoffDateTime, durationHours))
                .filter(ChauffeurCategoryOfferResponse::available)
                .toList();
    }

    private ChauffeurCategoryOfferResponse buildOffer(
            ChauffeurCategory category,
            LocalDateTime pickupDateTime,
            LocalDateTime dropoffDateTime,
            Integer durationHours) {

        // No passenger filter at the offers stage — passengers is unknown at browse time.
        List<Car> availableCars = carRepository.findAvailableChauffeurCars(
                category, pickupDateTime, dropoffDateTime, null, appClock.nowUtc());

        if (availableCars.isEmpty()) {
            return new ChauffeurCategoryOfferResponse(
                    category.getId(), category.getCode(), category.getName(),
                    category.getDescription(), category.getSeats(), category.getBags(),
                    category.isElectric(), category.getImageUrl(),
                    null, null, false);
        }

        BigDecimal minHourlyPrice = availableCars.stream()
                .map(Car::getHourlyPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        BigDecimal totalPrice = minHourlyPrice != null
                ? minHourlyPrice.multiply(BigDecimal.valueOf(durationHours))
                : null;

        return new ChauffeurCategoryOfferResponse(
                category.getId(), category.getCode(), category.getName(),
                category.getDescription(), category.getSeats(), category.getBags(),
                category.isElectric(), category.getImageUrl(),
                minHourlyPrice, totalPrice, true);
    }
}
