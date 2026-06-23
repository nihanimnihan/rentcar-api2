package com.rentcar.api.dto.booking;

import com.rentcar.api.domain.booking.BookingActorType;
import com.rentcar.api.domain.booking.BookingChannel;
import com.rentcar.api.domain.booking.BookingOptionType;
import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.booking.CancellationPolicyType;
import com.rentcar.api.domain.booking.MileageOption;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.dto.addon.BookingAddonResponse;
import com.rentcar.api.dto.car.CarResponse;
import com.rentcar.api.dto.customer.CustomerResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        String bookingReference,
        CarResponse car,
        CustomerResponse customer,
        LocalDateTime pickupDateTime,
        String pickupLocation,
        LocalDateTime dropoffDateTime,
        String dropoffLocation,
        int rentalDays,
        BigDecimal baseDailyPrice,
        BigDecimal effectiveDailyPrice,
        BigDecimal discountPercentage,
        BigDecimal carRentalTotal,
        BigDecimal oneWayFee,
        BigDecimal premiumLocationFee,
        BigDecimal tax,
        BigDecimal addonTotal,
        Long insurancePackageId,
        String insuranceCode,
        String insuranceNameSnapshot,
        BigDecimal insuranceDailyPriceSnapshot,
        BigDecimal insuranceTotalSnapshot,
        BigDecimal depositAmountSnapshot,
        BigDecimal totalPrice,
        int includedKmSnapshot,
        BigDecimal unlimitedKmPriceSnapshot,
        MileageOption mileageOption,
        /** Booking option tier — BEST_PRICE by default; STAY_FLEXIBLE when that feature is activated. */
        BookingOptionType bookingOptionType,
        /** Per-day flexibility fee. Zero for BEST_PRICE rental bookings; null only for legacy/non-rental rows. */
        BigDecimal bookingOptionDailyFee,
        /** Cancellation policy snapshot attached to the selected booking option. */
        CancellationPolicyType cancellationPolicyType,
        /** Transfer passenger count. Null for regular rental bookings. */
        Integer passengers,
        BookingStatus status,
        BookingSource source,
        List<BookingAddonResponse> addons,
        /** Latest payment status for this booking — null if no payment record exists. */
        PaymentStatus paymentStatus,
        /** Payment method used — null if no payment record exists. */
        PaymentMethod paymentMethod,
        // ── Audit metadata ────────────────────────────────────────────────────
        /** Who created this booking (null for transfer bookings). */
        BookingActorType createdByType,
        /** Channel through which booking was created (null for transfer bookings). */
        BookingChannel createdChannel,
        /** Who cancelled this booking — null until the booking is cancelled. */
        BookingActorType cancelledByType,
        /** Channel through which cancellation was performed — null until cancelled. */
        BookingChannel cancelledChannel,
        /** UTC instant when the booking was cancelled — null until cancelled. */
        Instant cancelledAt,
        /** Free-text reason for cancellation — null until cancelled. */
        String cancellationReason
) {
    /** Returns a new instance with payment fields replaced. Used to enrich mapper-produced responses. */
    public BookingResponse withPayment(PaymentStatus paymentStatus, PaymentMethod paymentMethod) {
        return new BookingResponse(
                id, bookingReference, car, customer,
                pickupDateTime, pickupLocation, dropoffDateTime, dropoffLocation,
                rentalDays, baseDailyPrice, effectiveDailyPrice, discountPercentage,
                carRentalTotal, oneWayFee, premiumLocationFee, tax, addonTotal,
                insurancePackageId, insuranceCode, insuranceNameSnapshot,
                insuranceDailyPriceSnapshot, insuranceTotalSnapshot, depositAmountSnapshot,
                totalPrice,
                includedKmSnapshot, unlimitedKmPriceSnapshot, mileageOption,
                bookingOptionType, bookingOptionDailyFee, cancellationPolicyType, passengers,
                status, source, addons,
                paymentStatus, paymentMethod,
                createdByType, createdChannel,
                cancelledByType, cancelledChannel, cancelledAt, cancellationReason
        );
    }
}
