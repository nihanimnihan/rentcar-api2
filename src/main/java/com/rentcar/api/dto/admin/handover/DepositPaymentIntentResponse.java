package com.rentcar.api.dto.admin.handover;

public record DepositPaymentIntentResponse(
        BookingDepositResponse deposit,
        String provider,
        String clientSecret,
        String paymentIntentId,
        String paymentLinkUrl
) {}
