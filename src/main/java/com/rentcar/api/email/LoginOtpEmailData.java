package com.rentcar.api.email;

public record LoginOtpEmailData(
        String customerEmail,
        String code,
        int expiresInMinutes,
        String language
) {
}
