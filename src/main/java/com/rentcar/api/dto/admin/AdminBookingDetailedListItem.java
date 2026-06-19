package com.rentcar.api.dto.admin;

import com.rentcar.api.domain.booking.BookingOptionType;
import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.booking.CancellationPolicyType;
import com.rentcar.api.domain.booking.MileageOption;
import com.rentcar.api.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detail projection used by the admin booking detail endpoint.
 * Carries the historical pricing snapshot so operations can explain a booking total.
 */
public record AdminBookingDetailedListItem(
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
        int rentalDays,
        BigDecimal baseDailyPrice,
        BigDecimal effectiveDailyPrice,
        BigDecimal discountPercentage,
        BigDecimal rentalCharge,
        BigDecimal oneWayFee,
        BigDecimal premiumLocationFee,
        BigDecimal tax,
        BigDecimal addonCharge,
        BigDecimal totalPrice,
        int includedKmSnapshot,
        BigDecimal unlimitedKmPriceSnapshot,
        MileageOption mileageOption,
        BookingOptionType bookingOptionType,
        BigDecimal bookingOptionDailyFee,
        CancellationPolicyType cancellationPolicyType,
        Integer passengers,
        Integer durationHours,
        BigDecimal hourlyPriceSnapshot,
        String chauffeurCategoryCode,
        String chauffeurCategoryName,
        String notes,
        String cancellationReason,
        /** Null when no payment record exists yet (intent not created). */
        PaymentStatus paymentStatus
) {}
