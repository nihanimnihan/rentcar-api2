package com.rentcar.api.dto.booking;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * Customer-facing cancellation request.
 * Authenticated by bookingReference + lastName — no numeric id exposed.
 */
public record ManageCancelRequest(
        @NotBlank String bookingReference,
        @NotBlank String lastName,
        @JsonAlias("reason") String cancellationReason
) {
}
