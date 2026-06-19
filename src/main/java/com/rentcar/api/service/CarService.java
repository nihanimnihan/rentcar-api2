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
    private final com.rentcar.api.util.AppClock appClock;

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
        log.debug("Car search: pickup={} dropoff={} vehicleType={} segment={} transmission={} fuelType={} premium={} guaranteedModel={}",
                request.getPickupLocation(), request.getDropoffDateTime(),
                request.getVehicleType(), request.getSegment(), request.getTransmission(), request.getFuelType(),
                request.getPremium(), request.getGuaranteedModel());
        if (request.getPickupDateTime() != null) {
            if (request.getPickupDateTime().isBefore(businessTimezone.nowBusinessLocal())) {
                throw new InvalidSearchDateException("Pickup date must not be in the past");
            }
            if (request.getDropoffDateTime() != null
                    && !request.getDropoffDateTime().isAfter(request.getPickupDateTime())) {
                throw new InvalidSearchDateException("Return date must be after pickup date");
            }
        }
        if (request.getPickupDateTime() != null && request.getDropoffDateTime() != null) {
            List<Car> results = carRepository.searchAvailableCars(
                    request.getPickupDateTime(),
                    request.getDropoffDateTime(),
                    request.getVehicleType(),
                    request.getSegment(),
                    request.getTransmission(),
                    request.getFuelType(),
                    request.getMinSeats(),
                    request.getMinBags(),
                    request.getMinDriverAge(),
                    request.getPremium(),
                    request.getGuaranteedModel(),
                    appClock.nowUtc()
            );
            log.debug("Car search returned {} available cars", results.size());
            return results;
        }
        List<Car> results = carRepository.searchCarsWithoutDateFilter(
                request.getVehicleType(),
                request.getSegment(),
                request.getTransmission(),
                request.getFuelType(),
                request.getMinSeats(),
                request.getMinBags(),
                request.getMinDriverAge(),
                request.getPremium(),
                request.getGuaranteedModel()
        );
        log.debug("Car search (no dates) returned {} cars", results.size());
        return results;
    }
}
