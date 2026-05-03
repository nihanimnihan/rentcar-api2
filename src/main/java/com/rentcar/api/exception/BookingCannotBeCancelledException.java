package com.rentcar.api.exception;

public class BookingCannotBeCancelledException extends RuntimeException {

    private final Long bookingId;

    public BookingCannotBeCancelledException(Long bookingId) {
        super("Booking cannot be cancelled");
        this.bookingId = bookingId;
    }

    public Long getBookingId() {
        return bookingId;
    }
}
