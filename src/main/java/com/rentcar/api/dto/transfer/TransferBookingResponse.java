package com.rentcar.api.dto.transfer;

import com.rentcar.api.domain.booking.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferBookingResponse(
        Long id,
        BookingStatus status,
        String customerName,
        String customerEmail,
        LocalDateTime pickupDateTime,
        LocalDateTime dropoffDateTime,
        int durationHours,
        String categoryCode,
        String categoryName,
        String assignedCarBrand,
        String assignedCarModel,
        int passengers,
        BigDecimal hourlyPrice,
        BigDecimal totalPrice,
        String notes
) {
}
