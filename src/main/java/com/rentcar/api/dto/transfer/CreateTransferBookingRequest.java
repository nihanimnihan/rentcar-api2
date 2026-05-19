package com.rentcar.api.dto.transfer;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateTransferBookingRequest(

        @NotBlank
        String customerName,

        @Email
        @NotBlank
        String customerEmail,

        @NotBlank
        String customerPhone,

        @NotNull
        @FutureOrPresent
        LocalDateTime pickupDateTime,

        @NotNull
        @Min(1)
        Integer durationHours,

        @NotNull
        Long categoryId,

        // Accepts both "passengers" and "passengerCount" from clients.
        // Nullable — defaults to 1 in the service if omitted.
        @Min(1)
        @JsonAlias("passengerCount")
        Integer passengers,

        // nullable — stored for reference, no business logic applied
        String notes
) {
}
