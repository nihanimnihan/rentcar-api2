package com.rentcar.api.dto.support;

import com.rentcar.api.domain.support.SupportRequestTopic;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupportRequestRequest(
        @NotNull
        SupportRequestTopic topic,

        @Size(max = 50)
        String bookingReference,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(max = 8)
        String phoneCountryCode,

        @NotBlank
        @Size(max = 24)
        String phoneNumber,

        @NotBlank
        @Size(max = 500)
        String message
) {
}
