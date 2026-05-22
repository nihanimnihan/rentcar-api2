package com.rentcar.api.exception;

public class BookingCannotBeCancelledException extends RuntimeException {

    private final Long bookingId;

    /** Used by the admin path where we have the numeric id. */
    public BookingCannotBeCancelledException(Long bookingId) {
        super("Booking cannot be cancelled");
        this.bookingId = bookingId;
    }

    /** Used by the customer-facing path where we surface a policy reason. */
    public BookingCannotBeCancelledException(String message) {
        super(message);
        this.bookingId = null;
    }

    public Long getBookingId() {
        return bookingId;
    }
}
