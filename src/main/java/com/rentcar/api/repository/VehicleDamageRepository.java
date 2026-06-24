package com.rentcar.api.repository;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.handover.VehicleDamage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleDamageRepository extends JpaRepository<VehicleDamage, Long> {

    List<VehicleDamage> findAllByCarAndActiveTrueOrderByIdAsc(Car car);
}
