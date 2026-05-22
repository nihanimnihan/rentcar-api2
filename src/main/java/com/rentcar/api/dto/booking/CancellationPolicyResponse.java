package com.rentcar.api.dto.booking;

import java.math.BigDecimal;

/**
 * Response DTO for the cancellation policy preview.
 * Returned by GET /api/bookings/manage/cancellation-policy.
 *
 * @param cancellable      whether this booking can currently be cancelled
 * @param reason           human-readable reason when cancellable=false; null otherwise
 * @param refundEligible   whether the customer is entitled to any refund
 * @param refundAmount     amount to be refunded (0.00 when not eligible)
 * @param cancellationFee  fee charged for cancelling (always 0.00 for MVP)
 * @param policyMessage    short description of the applicable policy
 */
public record CancellationPolicyResponse(
        boolean cancellable,
        String reason,
        boolean refundEligible,
        BigDecimal refundAmount,
        BigDecimal cancellationFee,
        String policyMessage
) {
}
