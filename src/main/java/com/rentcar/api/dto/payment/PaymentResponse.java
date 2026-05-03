package com.rentcar.api.dto.payment;

import com.rentcar.api.domain.payment.PaymentChannel;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.dto.booking.BookingResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        BigDecimal amount,
        String currencyCode,
        PaymentMethod method,
        PaymentChannel channel,
        PaymentStatus status,
        String providerReference,
        LocalDateTime paidAt,
        Long bookingId
) {

}
