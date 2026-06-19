package com.rentcar.api.dto.admin;

import com.rentcar.api.domain.booking.BookingOptionType;
import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.booking.CancellationPolicyType;
import com.rentcar.api.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lean projection used by the admin bookings list.
 * Includes enough pricing snapshot data to explain the total without opening the database.
 */
public record AdminBookingListItem(
        Long id,
        String bookingReference,
        BookingStatus status,
        BookingSource source,
        String customerName,
        String customerEmail,
        String carBrand,
        String carModel,
        LocalDateTime pickupDateTime,
        LocalDateTime dropoffDateTime,
        BigDecimal rentalCharge,
        BigDecimal oneWayFee,
        BigDecimal premiumLocationFee,
        BigDecimal tax,
        BigDecimal addonCharge,
        BigDecimal totalPrice,
        BookingOptionType bookingOptionType,
        BigDecimal bookingOptionDailyFee,
        CancellationPolicyType cancellationPolicyType,
        /** Null when no payment record exists yet (intent not created). */
        PaymentStatus paymentStatus
) {}
