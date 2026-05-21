package com.rentcar.api.exception;

public class BookingNotFoundException extends RuntimeException {
    private final Long bookingId;

    public BookingNotFoundException(Long bookingId) {
        super("Booking not found");
        this.bookingId = bookingId;
    }

    public BookingNotFoundException(String message) {
        super(message);
        this.bookingId = null;
    }

    public Long getBookingId() {
        return bookingId;
    }
}
