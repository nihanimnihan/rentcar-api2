package com.rentcar.api.repository;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.handover.VehicleHandover;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VehicleHandoverRepository extends JpaRepository<VehicleHandover, Long> {

    Optional<VehicleHandover> findByBooking(Booking booking);

    Optional<VehicleHandover> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM VehicleHandover h WHERE h.booking.id = :bookingId")
    Optional<VehicleHandover> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);
}
