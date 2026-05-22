package com.rentcar.api.dto.booking;

import com.rentcar.api.domain.booking.BookingOptionType;
import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.booking.MileageOption;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.dto.addon.BookingAddonResponse;
import com.rentcar.api.dto.car.CarResponse;
import com.rentcar.api.dto.customer.CustomerResponse;

import java.math.BigDecimal;
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
        BigDecimal addonTotal,
        BigDecimal totalPrice,
        int includedKmSnapshot,
        BigDecimal unlimitedKmPriceSnapshot,
        MileageOption mileageOption,
        /** Booking option tier — BEST_PRICE by default; STAY_FLEXIBLE when that feature is activated. */
        BookingOptionType bookingOptionType,
        BookingStatus status,
        BookingSource source,
        List<BookingAddonResponse> addons,
        /** Latest payment status for this booking — null if no payment record exists. */
        PaymentStatus paymentStatus,
        /** Payment method used — null if no payment record exists. */
        PaymentMethod paymentMethod
) {
    /** Returns a new instance with payment fields replaced. Used to enrich mapper-produced responses. */
    public BookingResponse withPayment(PaymentStatus paymentStatus, PaymentMethod paymentMethod) {
        return new BookingResponse(
                id, bookingReference, car, customer,
                pickupDateTime, pickupLocation, dropoffDateTime, dropoffLocation,
                rentalDays, baseDailyPrice, effectiveDailyPrice, discountPercentage,
                carRentalTotal, addonTotal, totalPrice,
                includedKmSnapshot, unlimitedKmPriceSnapshot, mileageOption,
                bookingOptionType, status, source, addons,
                paymentStatus, paymentMethod
        );
    }
}