package com.rentcar.api.dto.booking;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Customer-facing cancellation request.
 * Authenticated by either a manage token or bookingReference + lastName.
 * No numeric id is exposed.
 */
public record ManageCancelRequest(
        String bookingReference,
        String lastName,
        @JsonAlias("reason") String cancellationReason,
        @JsonAlias({"token", "manageBookingToken"}) String manageToken
) {
}
