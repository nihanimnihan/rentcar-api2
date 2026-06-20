package com.rentcar.api.dto.auth;

public record CompleteEmailProfileRequest(
        String profileToken,
        String firstName,
        String lastName,
        String phoneCountryCode,
        String phoneNumber
) {
}
