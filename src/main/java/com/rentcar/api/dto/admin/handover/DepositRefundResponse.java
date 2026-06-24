package com.rentcar.api.dto.admin.handover;

import com.rentcar.api.domain.handover.DepositRefundType;

import java.math.BigDecimal;
import java.time.Instant;

public record DepositRefundResponse(
        Long id,
        Long depositId,
        DepositRefundType type,
        BigDecimal amount,
        String note,
        String stripeRefundId,
        Instant createdAt,
        String createdBy
) {}
