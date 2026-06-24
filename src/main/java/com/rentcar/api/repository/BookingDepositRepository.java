package com.rentcar.api.repository;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.handover.BookingDeposit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookingDepositRepository extends JpaRepository<BookingDeposit, Long> {

    Optional<BookingDeposit> findByBooking(Booking booking);

    Optional<BookingDeposit> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM BookingDeposit d WHERE d.id = :id")
    Optional<BookingDeposit> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM BookingDeposit d WHERE d.booking.id = :bookingId")
    Optional<BookingDeposit> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM BookingDeposit d WHERE d.stripePaymentIntentId = :stripePaymentIntentId")
    Optional<BookingDeposit> findByStripePaymentIntentIdForUpdate(@Param("stripePaymentIntentId") String stripePaymentIntentId);
}
