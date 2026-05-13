package com.rentcar.api.service;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.dto.car.CarSearchRequest;
import com.rentcar.api.exception.CarNotFoundException;
import com.rentcar.api.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CarService {

    private static final int POPULAR_CARS_LIMIT = 4;

    private final CarRepository carRepository;
    private final PricingService pricingService;

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
        List<Car> cars = carRepository.findPopularCars();
        return cars.subList(0, Math.min(cars.size(), POPULAR_CARS_LIMIT));
    }

    public List<Car> searchCars(CarSearchRequest request) {
        return carRepository.findByActiveTrue()
                .stream()
                .filter(car -> matchesFilters(car, request))
                .toList();
    }

    private boolean matchesFilters(Car car, CarSearchRequest request) {
        if (request.vehicleType() != null && car.getVehicleType() != request.vehicleType()) {
            return false;
        }
        if (request.segment() != null && car.getSegment() != request.segment()) {
            return false;
        }
        if (request.transmission() != null && car.getTransmission() != request.transmission()) {
            return false;
        }
        if (request.fuelType() != null && car.getFuelType() != request.fuelType()) {
            return false;
        }
        if (request.minSeats() != null && car.getSeats() < request.minSeats()) {
            return false;
        }
        if (request.minBags() != null && car.getBags() < request.minBags()) {
            return false;
        }
        return request.minDriverAge() == null || car.getMinDriverAge() <= request.minDriverAge();
    }
}