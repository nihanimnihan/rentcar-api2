package com.rentcar.api.dto.booking;

import jakarta.validation.constraints.NotBlank;

/**
 * Customer-facing cancellation request.
 * Authenticated by bookingReference + lastName — no numeric id exposed.
 */
public record ManageCancelRequest(
        @NotBlank String bookingReference,
        @NotBlank String lastName
) {
}
