package com.rentcar.api.dto.booking;

import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.dto.addon.BookingAddonResponse;
import com.rentcar.api.dto.car.CarResponse;
import com.rentcar.api.dto.customer.CustomerResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        CarResponse car,
        CustomerResponse customer,
        LocalDateTime pickupDateTime,
        LocalDateTime dropoffDateTime,
        int rentalDays,
        BigDecimal baseDailyPrice,
        BigDecimal effectiveDailyPrice,
        BigDecimal discountPercentage,
        BigDecimal carRentalTotal,
        BigDecimal addonTotal,
        BigDecimal totalPrice,
        BookingStatus status,
        BookingSource source,
        List<BookingAddonResponse> addons
) {
}