package com.rentcar.api.controller;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.dto.car.CarDetailResponse;
import com.rentcar.api.dto.car.CarResponse;
import com.rentcar.api.dto.car.CarSearchRequest;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import com.rentcar.api.mapper.CarMapper;
import com.rentcar.api.service.CarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
public class CarController {

    private final CarService carService;
    private final CarMapper carMapper;

    @GetMapping
    public List<CarResponse> getCars() {
        return carService.getActiveCars().stream().map(carMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public CarDetailResponse getCarById(@PathVariable Long id, CarSearchRequest request) {
        Car car = carService.getActiveCarById(id);
        CarDetailResponse response = carMapper.toDetailResponse(car);
        applyPricing(response, carService.calculatePrice(car, request));
        return response;
    }

    @GetMapping("/search")
    public List<CarResponse> searchCars(CarSearchRequest request) {
        return carService.searchCars(request)
                .stream()
                .map(car -> {
                    CarResponse response = carMapper.toResponse(car);
                    PriceBreakdown price = carService.calculatePrice(car, request);
                    response.setDailyPrice(price.effectiveDailyPrice());
                    response.setTotalPrice(price.totalPrice());
                    return response;
                })
                .toList();
    }

    @GetMapping("/popular")
    public List<CarResponse> getPopularCars() {
        return carService.getPopularCars().stream().map(carMapper::toResponse).toList();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void applyPricing(CarDetailResponse response, PriceBreakdown price) {
        response.setDailyPrice(price.effectiveDailyPrice());
        response.setTotalPrice(price.totalPrice());
        response.setPriceBreakdown(price);
    }
}
