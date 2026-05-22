package com.rentcar.api.dto.payment;

import com.rentcar.api.domain.payment.PaymentChannel;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.dto.booking.BookingResponse;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        String paymentReference,
        BigDecimal amount,
        String currencyCode,
        PaymentMethod method,
        PaymentChannel channel,
        PaymentStatus status,
        String providerReference,
        Instant paidAt,
        Long bookingId
) {

}
