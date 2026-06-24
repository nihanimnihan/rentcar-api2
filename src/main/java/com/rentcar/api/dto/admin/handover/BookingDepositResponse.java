package com.rentcar.api.dto.admin.handover;

import com.rentcar.api.domain.handover.BookingDepositMethod;
import com.rentcar.api.domain.handover.BookingDepositStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingDepositResponse(
        Long id,
        Long bookingId,
        BigDecimal amount,
        String currency,
        BookingDepositMethod method,
        BookingDepositStatus status,
        String stripePaymentIntentId,
        String stripeCheckoutSessionId,
        String stripePaymentLinkUrl,
        String stripePaymentReference,
        String adminNote,
        Instant collectedAt,
        Instant refundDeadlineAt,
        BigDecimal refundedAmount,
        BigDecimal remainingAmount,
        Instant createdAt,
        Instant updatedAt,
        List<DepositRefundResponse> refunds
) {}
