package com.rentcar.api.repository;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.car.FuelType;
import com.rentcar.api.domain.car.TransmissionType;
import com.rentcar.api.domain.car.VehicleSegment;
import com.rentcar.api.domain.car.VehicleType;
import com.rentcar.api.domain.transfer.ChauffeurCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarRepository extends JpaRepository<Car, Long> {

    List<Car> findByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Car c WHERE c.id = :id")
    Optional<Car> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT c
        FROM Car c
        LEFT JOIN Booking b ON b.car = c AND b.status = com.rentcar.api.domain.booking.BookingStatus.CONFIRMED
        GROUP BY c
        ORDER BY count(b.id) DESC
    """)
    List<Car> findPopularCars(Pageable pageable);

    /**
     * Search with availability date filter.
     * Both pickupDateTime and dropoffDateTime are required (non-null).
     * Excludes cars with an overlapping PENDING or CONFIRMED booking.
     */
    @Query("""
        SELECT c FROM Car c
        WHERE c.active = true
        AND (:vehicleType   IS NULL OR c.vehicleType   = :vehicleType)
        AND (:segment       IS NULL OR c.segment       = :segment)
        AND (:transmission  IS NULL OR c.transmission  = :transmission)
        AND (:fuelType      IS NULL OR c.fuelType      = :fuelType)
        AND (:minSeats      IS NULL OR c.seats         >= :minSeats)
        AND (:minBags       IS NULL OR c.bags          >= :minBags)
        AND (:minDriverAge  IS NULL OR c.minDriverAge  <= :minDriverAge)
        AND NOT EXISTS (
            SELECT b FROM Booking b
            WHERE b.car = c
            AND b.status IN (
                com.rentcar.api.domain.booking.BookingStatus.PENDING,
                com.rentcar.api.domain.booking.BookingStatus.CONFIRMED
            )
            AND b.pickupDateTime  < :dropoffDateTime
            AND b.dropoffDateTime > :pickupDateTime
        )
        ORDER BY c.dailyPrice ASC
    """)
    List<Car> searchAvailableCars(
            @Param("pickupDateTime")  LocalDateTime pickupDateTime,
            @Param("dropoffDateTime") LocalDateTime dropoffDateTime,
            @Param("vehicleType")     VehicleType vehicleType,
            @Param("segment")         VehicleSegment segment,
            @Param("transmission")    TransmissionType transmission,
            @Param("fuelType")        FuelType fuelType,
            @Param("minSeats")        Integer minSeats,
            @Param("minBags")         Integer minBags,
            @Param("minDriverAge")    Integer minDriverAge
    );

    /**
     * Search without date availability filter (used when dates are not provided).
     */
    @Query("""
        SELECT c FROM Car c
        WHERE c.active = true
        AND (:vehicleType   IS NULL OR c.vehicleType   = :vehicleType)
        AND (:segment       IS NULL OR c.segment       = :segment)
        AND (:transmission  IS NULL OR c.transmission  = :transmission)
        AND (:fuelType      IS NULL OR c.fuelType      = :fuelType)
        AND (:minSeats      IS NULL OR c.seats         >= :minSeats)
        AND (:minBags       IS NULL OR c.bags          >= :minBags)
        AND (:minDriverAge  IS NULL OR c.minDriverAge  <= :minDriverAge)
        ORDER BY c.dailyPrice ASC
    """)
    List<Car> searchCarsWithoutDateFilter(
            @Param("vehicleType")     VehicleType vehicleType,
            @Param("segment")         VehicleSegment segment,
            @Param("transmission")    TransmissionType transmission,
            @Param("fuelType")        FuelType fuelType,
            @Param("minSeats")        Integer minSeats,
            @Param("minBags")         Integer minBags,
            @Param("minDriverAge")    Integer minDriverAge
    );

    /**
     * Returns chauffeur-available cars for a given category, excluding those
     * with overlapping PENDING or CONFIRMED bookings in the requested window.
     * Results are ordered by hourlyPrice ASC so the service can pick the minimum.
     */
    @Query("""
        SELECT c FROM Car c
        WHERE c.active = true
        AND c.chauffeurAvailable = true
        AND c.chauffeurCategory = :category
        AND NOT EXISTS (
            SELECT b FROM Booking b
            WHERE b.car = c
            AND b.status IN (
                com.rentcar.api.domain.booking.BookingStatus.PENDING,
                com.rentcar.api.domain.booking.BookingStatus.CONFIRMED
            )
            AND b.pickupDateTime  < :dropoffDateTime
            AND b.dropoffDateTime > :pickupDateTime
        )
        ORDER BY c.hourlyPrice ASC
    """)
    List<Car> findAvailableChauffeurCars(
            @Param("category")        ChauffeurCategory category,
            @Param("pickupDateTime")  LocalDateTime pickupDateTime,
            @Param("dropoffDateTime") LocalDateTime dropoffDateTime
    );
}