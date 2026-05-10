package com.rentcar.api.mapper;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.dto.car.CarDetailResponse;
import com.rentcar.api.dto.car.CarResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CarMapper {
    CarResponse toResponse(Car car);
    CarDetailResponse toDetailResponse(Car car);
}