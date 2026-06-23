package com.rentcar.api.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AdminCreateBookingRequest(
        @NotNull Long vehicleId,
        @NotBlank String pickupLocation,
        String pickupAddress,
        String pickupPlaceId,
        @NotNull LocalDate pickupDate,
        @NotNull LocalTime pickupTime,
        @NotBlank String returnLocation,
        String returnAddress,
        String returnPlaceId,
        @NotNull LocalDate returnDate,
        @NotNull LocalTime returnTime,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email @NotBlank String email,
        @NotBlank String phoneCountryCode,
        @NotBlank String phoneNumber,
        @NotNull Long insurancePackageId,
        List<Long> addonIds,
        @NotNull @DecimalMin("0.01") BigDecimal totalPrice,
        @NotNull AdminPaymentSource paymentSource,
        String internalNote
) {
}
