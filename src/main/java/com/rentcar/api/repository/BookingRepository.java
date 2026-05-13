package com.rentcar.api.repository;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.car.Car;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCarAndStatusInAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
            Car car,
            List<BookingStatus> statuses,
            LocalDateTime newDropoff,
            LocalDateTime newPickup
    );
}