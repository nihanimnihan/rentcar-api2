package com.rentcar.api.dto.booking;

import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.dto.car.CarResponse;
import com.rentcar.api.dto.customer.CustomerResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        CarResponse car,
        CustomerResponse customer,
        LocalDateTime pickupDateTime,
        LocalDateTime dropoffDateTime,
        BigDecimal dailyPrice,
        BigDecimal totalPrice,
        int rentalDays,
        BookingStatus status,
        BookingSource source
) {
}