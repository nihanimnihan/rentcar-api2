package com.rentcar.api.service;

import java.math.BigDecimal;

public record CancellationPolicyDecision(
        boolean cancellable,
        String reason,
        boolean refundEligible,
        BigDecimal refundAmount,
        BigDecimal cancellationFee,
        String policyMessage,
        boolean adminOperationalCancellationAllowed,
        boolean noShow,
        String cancellationReason
) {
}
