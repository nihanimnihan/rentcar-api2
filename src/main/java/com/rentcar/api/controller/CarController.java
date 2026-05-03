package com.rentcar.api.controller;

import com.rentcar.api.dto.car.CarResponse;
import com.rentcar.api.dto.car.CarSearchRequest;
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
        return  carService.getActiveCars().stream().map(carMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public CarResponse getCarById(@PathVariable Long id) {
        return carMapper.toResponse(carService.getCarById(id));
    }

    @GetMapping("/search")
    public List<CarResponse> searchCars(CarSearchRequest request) {
        return  carService.getActiveCars().stream().map(carMapper::toResponse).toList();

        //return carService.searchAvailableCars(request);
    }
}