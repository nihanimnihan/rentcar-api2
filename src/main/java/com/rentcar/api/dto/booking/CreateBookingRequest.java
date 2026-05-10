package com.rentcar.api.dto.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateBookingRequest(

        @NotNull
        Long carId,

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
        @FutureOrPresent
        LocalDateTime dropoffDateTime,

        @NotNull
        String pickupLocation,

        @NotNull
        String dropoffLocation
) {
}