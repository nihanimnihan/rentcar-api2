package com.rentcar.api.service;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.transfer.ChauffeurCategory;
import com.rentcar.api.dto.admin.AdminCarRequest;
import com.rentcar.api.dto.admin.AdminCarResponse;
import com.rentcar.api.dto.admin.handover.AdminVehicleDamageRequest;
import com.rentcar.api.dto.admin.handover.VehicleDamageResponse;
import com.rentcar.api.exception.CarNotFoundException;
import com.rentcar.api.exception.ChauffeurCategoryNotFoundException;
import com.rentcar.api.exception.InvalidCarConfigurationException;
import com.rentcar.api.repository.CarRepository;
import com.rentcar.api.repository.ChauffeurCategoryRepository;
import com.rentcar.api.repository.VehicleDamageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCarService {

    private final CarRepository carRepository;
    private final ChauffeurCategoryRepository chauffeurCategoryRepository;
    private final VehicleDamageRepository vehicleDamageRepository;

    public List<AdminCarResponse> list() {
        return carRepository.findAllOrderByIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public AdminCarResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public AdminCarResponse create(AdminCarRequest request) {
        ChauffeurCategory category = resolveChauffeurCategory(request);

        Car car = Car.builder()
                .brand(request.brand())
                .model(request.model())
                .segment(request.segment())
                .vehicleType(request.vehicleType())
                .transmission(request.transmission())
                .fuelType(request.fuelType())
                .seats(request.seats())
                .bags(request.bags())
                .doors(request.doors())
                .minDriverAge(request.minDriverAge())
                .airConditioning(request.airConditioning())
                .premium(request.premium())
                .guaranteedModel(request.guaranteedModel())
                .dailyPrice(request.dailyPrice())
                .active(request.active())
                .displayClass(request.displayClass())
                .imageUrl(request.imageUrl())
                .chauffeurAvailable(request.chauffeurAvailable())
                .chauffeurCategory(category)
                .hourlyPrice(request.hourlyPrice())
                .build();

        return toResponse(carRepository.save(car));
    }

    @Transactional
    public AdminCarResponse update(Long id, AdminCarRequest request) {
        Car car = findOrThrow(id);
        ChauffeurCategory category = resolveChauffeurCategory(request);

        car.setBrand(request.brand());
        car.setModel(request.model());
        car.setSegment(request.segment());
        car.setVehicleType(request.vehicleType());
        car.setTransmission(request.transmission());
        car.setFuelType(request.fuelType());
        car.setSeats(request.seats());
        car.setBags(request.bags());
        car.setDoors(request.doors());
        car.setMinDriverAge(request.minDriverAge());
        car.setAirConditioning(request.airConditioning());
        car.setPremium(request.premium());
        car.setGuaranteedModel(request.guaranteedModel());
        car.setDailyPrice(request.dailyPrice());
        car.setActive(request.active());
        car.setDisplayClass(request.displayClass());
        car.setImageUrl(request.imageUrl());
        car.setChauffeurAvailable(request.chauffeurAvailable());
        car.setChauffeurCategory(category);
        car.setHourlyPrice(request.hourlyPrice());

        return toResponse(carRepository.save(car));
    }

    @Transactional
    public AdminCarResponse setActive(Long id, boolean active) {
        Car car = findOrThrow(id);
        car.setActive(active);
        return toResponse(carRepository.save(car));
    }

    /**
     * Always soft-deletes by setting active=false.
     * Cars may be referenced by historical Booking records; physical deletion is not supported.
     */
    @Transactional
    public void delete(Long id) {
        Car car = findOrThrow(id);
        car.setActive(false);
        carRepository.save(car);
    }

    public List<ChauffeurCategory> listChauffeurCategories() {
        return chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    public List<VehicleDamageResponse> listDamages(Long carId) {
        Car car = findOrThrow(carId);
        return vehicleDamageRepository.findAllByCarAndActiveTrueOrderByIdAsc(car).stream()
                .map(this::toDamageResponse)
                .toList();
    }

    @Transactional
    public VehicleDamageResponse createDamage(Long carId, AdminVehicleDamageRequest request) {
        Car car = findOrThrow(carId);
        var damage = com.rentcar.api.domain.handover.VehicleDamage.builder()
                .car(car)
                .damageCode(request.damageCode())
                .title(request.title())
                .description(blankToNull(request.description()))
                .location(blankToNull(request.location()))
                .severity(request.severity())
                .active(request.active())
                .build();
        return toDamageResponse(vehicleDamageRepository.save(damage));
    }

    @Transactional
    public VehicleDamageResponse updateDamage(Long carId, Long damageId, AdminVehicleDamageRequest request) {
        Car car = findOrThrow(carId);
        var damage = vehicleDamageRepository.findById(damageId)
                .orElseThrow(() -> new IllegalArgumentException("Damage not found with id: " + damageId));
        if (!damage.getCar().getId().equals(car.getId())) {
            throw new IllegalArgumentException("Damage does not belong to car " + carId);
        }
        damage.setDamageCode(request.damageCode());
        damage.setTitle(request.title());
        damage.setDescription(blankToNull(request.description()));
        damage.setLocation(blankToNull(request.location()));
        damage.setSeverity(request.severity());
        damage.setActive(request.active());
        return toDamageResponse(vehicleDamageRepository.save(damage));
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private Car findOrThrow(Long id) {
        return carRepository.findById(id)
                .orElseThrow(() -> new CarNotFoundException(id));
    }

    /**
     * Validates and resolves chauffeur configuration:
     * - chauffeurAvailable=true  → chauffeurCategoryId required, hourlyPrice required > 0
     * - chauffeurAvailable=false → chauffeurCategoryId ignored, hourlyPrice ignored
     */
    private ChauffeurCategory resolveChauffeurCategory(AdminCarRequest request) {
        if (!request.chauffeurAvailable()) {
            return null;
        }
        if (request.chauffeurCategoryId() == null) {
            throw new InvalidCarConfigurationException(
                    "chauffeurCategoryId is required when chauffeurAvailable is true.");
        }
        if (request.hourlyPrice() == null) {
            throw new InvalidCarConfigurationException(
                    "hourlyPrice is required when chauffeurAvailable is true.");
        }
        return chauffeurCategoryRepository.findById(request.chauffeurCategoryId())
                .orElseThrow(() -> new ChauffeurCategoryNotFoundException(request.chauffeurCategoryId()));
    }

    private AdminCarResponse toResponse(Car c) {
        AdminCarResponse.CategoryRef catRef = c.getChauffeurCategory() == null ? null
                : new AdminCarResponse.CategoryRef(
                        c.getChauffeurCategory().getId(),
                        c.getChauffeurCategory().getCode(),
                        c.getChauffeurCategory().getName());

        return new AdminCarResponse(
                c.getId(),
                c.getBrand(),
                c.getModel(),
                c.getSegment() != null ? c.getSegment().name() : null,
                c.getVehicleType() != null ? c.getVehicleType().name() : null,
                c.getTransmission() != null ? c.getTransmission().name() : null,
                c.getFuelType() != null ? c.getFuelType().name() : null,
                c.getSeats(),
                c.getBags(),
                c.getDoors(),
                c.getMinDriverAge(),
                c.getAirConditioning(),
                c.getPremium(),
                c.getGuaranteedModel(),
                c.getDailyPrice(),
                c.getActive(),
                c.getDisplayClass(),
                c.getImageUrl(),
                c.isChauffeurAvailable(),
                catRef,
                c.getHourlyPrice(),
                vehicleDamageRepository.findAllByCarAndActiveTrueOrderByIdAsc(c).stream()
                        .map(this::toDamageResponse)
                        .toList()
        );
    }

    private VehicleDamageResponse toDamageResponse(com.rentcar.api.domain.handover.VehicleDamage damage) {
        return new VehicleDamageResponse(
                damage.getId(),
                damage.getCar().getId(),
                damage.getDamageCode(),
                damage.getTitle(),
                damage.getDescription(),
                damage.getLocation(),
                damage.getSeverity(),
                damage.isActive(),
                damage.getCreatedAt(),
                damage.getUpdatedAt()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
