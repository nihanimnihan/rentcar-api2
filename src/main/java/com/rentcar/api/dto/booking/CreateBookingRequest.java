package com.rentcar.api.dto.booking;

import com.rentcar.api.domain.booking.MileageOption;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

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
        String dropoffLocation,

        // nullable — no add-ons selected is valid
        List<Long> addonIds,

        // nullable — defaults to INCLUDED when not provided
        MileageOption mileageOption
) {
}