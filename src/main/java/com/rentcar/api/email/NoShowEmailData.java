package com.rentcar.api.email;

public record NoShowEmailData(
        String bookingReference,
        String customerEmail,
        String customerName,
        String managementUrl,
        String language
) {
}
