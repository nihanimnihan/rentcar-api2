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

    boolean existsByBookingReference(String bookingReference);

    @Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.customer " +
           "JOIN FETCH b.car " +
           "LEFT JOIN FETCH b.rentalDetails " +
           "LEFT JOIN FETCH b.transferDetails " +
           "WHERE b.bookingReference = :ref")
    Optional<Booking> findByBookingReferenceEager(@Param("ref") String ref);

    @Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.customer " +
           "JOIN FETCH b.car " +
           "LEFT JOIN FETCH b.rentalDetails " +
           "LEFT JOIN FETCH b.transferDetails " +
           "WHERE b.id = :id")
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    boolean existsByCarAndStatusInAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
            Car car,
            List<BookingStatus> statuses,
            LocalDateTime newDropoff,
            LocalDateTime newPickup
    );

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Booking b WHERE b.car = :car " +
            "AND (b.status = com.rentcar.api.domain.booking.BookingStatus.CONFIRMED OR (b.status = com.rentcar.api.domain.booking.BookingStatus.PENDING AND b.expiresAt > :now)) " +
            "AND b.pickupDateTime < :newDropoff AND b.dropoffDateTime > :newPickup")
    boolean existsByCarAndActiveStatusAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
            Car car,
            LocalDateTime newDropoff,
            LocalDateTime newPickup,
            java.time.Instant now
    );

    @Query("SELECT b FROM Booking b WHERE b.status = com.rentcar.api.domain.booking.BookingStatus.PENDING AND b.expiresAt < :now")
    java.util.List<Booking> findPendingBookingsExpired(java.time.Instant now);

    boolean existsByCheckoutSessionToken(String checkoutSessionToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id AND b.checkoutSessionToken = :token")
    Optional<Booking> findByIdAndCheckoutSessionTokenForUpdate(@Param("id") Long id, @Param("token") String token);

    /**
     * Returns all bookings, newest first, with customer and car eagerly fetched
     * to avoid N+1 queries when mapping to {@code AdminBookingListItem}.
     */
    @Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.customer " +
           "JOIN FETCH b.car " +
           "LEFT JOIN FETCH b.rentalDetails " +
           "LEFT JOIN FETCH b.transferDetails " +
           "ORDER BY b.createdAt DESC")
    List<Booking> findAllOrderByCreatedAtDesc();

    @Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.customer " +
           "JOIN FETCH b.car " +
           "LEFT JOIN FETCH b.rentalDetails " +
           "LEFT JOIN FETCH b.transferDetails " +
           "WHERE b.customer.email = :email ORDER BY b.createdAt DESC")
    List<Booking> findByCustomerEmailOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("email") String email);
}
