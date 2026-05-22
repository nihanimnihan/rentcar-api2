package com.rentcar.api.dto.admin;

import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lean projection used by the admin bookings list.
 * Omits internal pricing breakdowns and addon detail — use GET /api/bookings/{id} for full detail.
 */
public record AdminBookingListItem(
        Long id,
        String bookingReference,
        BookingStatus status,
        String customerName,
        String customerEmail,
        String carBrand,
        String carModel,
        LocalDateTime pickupDateTime,
        LocalDateTime dropoffDateTime,
        BigDecimal totalPrice,
        /** Null when no payment record exists yet (intent not created). */
        PaymentStatus paymentStatus
) {}
