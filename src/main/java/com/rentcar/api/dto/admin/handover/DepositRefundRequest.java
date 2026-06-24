package com.rentcar.api.dto.admin.handover;

import com.rentcar.api.domain.handover.DepositRefundType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRefundRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal refundAmount,
        @NotNull DepositRefundType type,
        String note
) {}
