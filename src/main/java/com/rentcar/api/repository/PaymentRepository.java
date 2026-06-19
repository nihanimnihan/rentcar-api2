package com.rentcar.api.repository;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.payment.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Returns the most recent payment for a booking.
     *
     * <p>Ordered by {@code createdAt DESC, id DESC} to guarantee determinism when two
     * payments share the same millisecond timestamp (possible during fast retries or tests).
     * The {@code id} tiebreaker is safe because IDs are monotonically increasing.
     */
    Optional<Payment> findTopByBookingOrderByCreatedAtDescIdDesc(Booking booking);

    /**
     * Returns the full payment history for a booking, newest first.
     *
     * <p>Same dual-sort as {@link #findTopByBookingOrderByCreatedAtDescIdDesc} for determinism.
     */
    List<Payment> findAllByBookingOrderByCreatedAtDescIdDesc(Booking booking);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.stripePaymentIntentId = :stripePaymentIntentId")
    Optional<Payment> findByStripePaymentIntentIdForUpdate(@Param("stripePaymentIntentId") String stripePaymentIntentId);
}
