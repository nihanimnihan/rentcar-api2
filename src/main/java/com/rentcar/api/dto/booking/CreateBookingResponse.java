package com.rentcar.api.dto.booking;

import com.rentcar.api.domain.booking.BookingStatus;

public record CreateBookingResponse(
        Long id,
        String bookingReference,
        BookingStatus status,
        String checkoutSessionToken
) {
}
