package com.rentcar.api.repository;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.car.Car;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCarIdAndStatusIn(Long carId, List<BookingStatus> statuses);

    boolean existsByCarAndStatusInAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
            Car car,
            List<BookingStatus> statuses,
            LocalDateTime newDropoff,
            LocalDateTime newPickup
    );
}