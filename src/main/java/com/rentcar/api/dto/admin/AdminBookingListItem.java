package com.rentcar.api.dto.admin;

import com.rentcar.api.domain.booking.BookingOptionType;
import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.booking.CancellationPolicyType;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.domain.payment.PaymentMethod;

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
        BigDecimal insuranceTotalSnapshot,
        BigDecimal totalPrice,
        BookingOptionType bookingOptionType,
        BigDecimal bookingOptionDailyFee,
        CancellationPolicyType cancellationPolicyType,
        boolean cancellationAllowed,
        boolean refundEligible,
        BigDecimal refundAmount,
        boolean noShow,
        /** Null when no payment record exists yet (intent not created). */
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod
) {}
