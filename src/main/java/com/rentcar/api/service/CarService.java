package com.rentcar.api.service;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.dto.car.CarSearchRequest;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import com.rentcar.api.exception.CarNotFoundException;
import com.rentcar.api.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rentcar.api.exception.InvalidSearchDateException;

import com.rentcar.api.util.BusinessTimezone;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarService {

    private static final int POPULAR_CARS_LIMIT = 4;

    private final CarRepository carRepository;
    private final PricingService pricingService;
    private final BusinessTimezone businessTimezone;

    public List<Car> getActiveCars() {
        return carRepository.findByActiveTrue();
    }

    public Car getCarById(Long id) {
        return carRepository.findById(id).orElseThrow(() -> new CarNotFoundException(id));
    }

    public Car getActiveCarById(Long id) {
        Car car = getCarById(id);

        if (!car.getActive()) {
            throw new CarNotFoundException(id);
        }
        return car;
    }

    /**
     * Fetches an active car using a PESSIMISTIC_WRITE lock.
     * Must be called within an existing transaction.
     * Use this method in booking creation to prevent double-booking races.
     */
    @Transactional
    public Car getActiveCarByIdForUpdate(Long id) {
        Car car = carRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CarNotFoundException(id));

        if (!car.getActive()) {
            throw new CarNotFoundException(id);
        }
        return car;
    }

    public List<Car> getPopularCars() {
        return carRepository.findPopularCars(PageRequest.of(0, POPULAR_CARS_LIMIT));
    }

    /**
     * Calculates the price breakdown for a car given search parameters.
     * Delegates to PricingService; centralises all pricing calls through CarService
     * so the controller does not need a direct PricingService dependency.
     */
    public PriceBreakdown calculatePrice(Car car, CarSearchRequest request) {
        return pricingService.calculate(car, request);
    }

    public List<Car> searchCars(CarSearchRequest request) {
        log.debug("Car search: pickup={} dropoff={} vehicleType={} segment={} transmission={} fuelType={}",
                request.pickupDateTime(), request.dropoffDateTime(),
                request.vehicleType(), request.segment(), request.transmission(), request.fuelType());
        if (request.pickupDateTime() != null) {
            if (request.pickupDateTime().isBefore(businessTimezone.nowBusinessLocal())) {
                throw new InvalidSearchDateException("Pickup date must not be in the past");
            }
            if (request.dropoffDateTime() != null
                    && !request.dropoffDateTime().isAfter(request.pickupDateTime())) {
                throw new InvalidSearchDateException("Return date must be after pickup date");
            }
        }
        if (request.pickupDateTime() != null && request.dropoffDateTime() != null) {
            List<Car> results = carRepository.searchAvailableCars(
                    request.pickupDateTime(),
                    request.dropoffDateTime(),
                    request.vehicleType(),
                    request.segment(),
                    request.transmission(),
                    request.fuelType(),
                    request.minSeats(),
                    request.minBags(),
                    request.minDriverAge()
            );
            log.debug("Car search returned {} available cars", results.size());
            return results;
        }
        List<Car> results = carRepository.searchCarsWithoutDateFilter(
                request.vehicleType(),
                request.segment(),
                request.transmission(),
                request.fuelType(),
                request.minSeats(),
                request.minBags(),
                request.minDriverAge()
        );
        log.debug("Car search (no dates) returned {} cars", results.size());
        return results;
    }
}