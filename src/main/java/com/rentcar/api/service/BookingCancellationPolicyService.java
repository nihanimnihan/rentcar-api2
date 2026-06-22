package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.dto.booking.CancellationPolicyResponse;
import com.rentcar.api.util.BusinessTimezone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BookingCancellationPolicyService {

    private static final String REASON_ALREADY_CANCELLED = "Booking is already cancelled.";
    private static final String REASON_PICKUP_PASSED = "Your pickup date has passed.";
    private static final String REASON_WINDOW_EXPIRED = "Cancellation window has expired.";
    private static final String MESSAGE_ALREADY_CANCELLED =
            "This booking has already been cancelled.";
    private static final String MESSAGE_PICKUP_PASSED =
            "The booking can no longer be modified after the pickup date.";
    private static final String MESSAGE_FULL_REFUND = "Full refund will be applied.";
    private static final String MESSAGE_NO_CHARGE =
            "Your booking has not been paid — no charge applies.";
    private static final String MESSAGE_WINDOW_EXPIRED =
            "Cancellation window has expired. This booking is no longer refundable.";
    private static final String MESSAGE_NO_SHOW =
            "No-show recorded. No refund applies.";

    private final BusinessTimezone businessTimezone;

    public CancellationPolicyDecision evaluateCustomerPolicy(Booking booking) {
        return evaluate(booking, false);
    }

    public CancellationPolicyDecision evaluateAdminPolicy(Booking booking) {
        return evaluate(booking, true);
    }

    public CancellationPolicyDecision evaluateNoShow(Booking booking) {
        return new CancellationPolicyDecision(
                canTransitionToCancelled(booking),
                canTransitionToCancelled(booking) ? null : REASON_ALREADY_CANCELLED,
                false,
                money(BigDecimal.ZERO),
                money(BigDecimal.ZERO),
                MESSAGE_NO_SHOW,
                canTransitionToCancelled(booking),
                true,
                "NO_SHOW");
    }

    public CancellationPolicyResponse toResponse(CancellationPolicyDecision decision) {
        return new CancellationPolicyResponse(
                decision.cancellable(),
                decision.reason(),
                decision.refundEligible(),
                decision.refundAmount(),
                decision.cancellationFee(),
                decision.policyMessage());
    }

    private CancellationPolicyDecision evaluate(Booking booking, boolean admin) {
        LocalDateTime now = businessTimezone.nowBusinessLocal();

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return blocked(REASON_ALREADY_CANCELLED, MESSAGE_ALREADY_CANCELLED, false);
        }

        if (booking.getPickupDateTime().isBefore(now)) {
            boolean adminAllowed = admin && canTransitionToCancelled(booking);
            return new CancellationPolicyDecision(
                    adminAllowed,
                    adminAllowed ? null : REASON_PICKUP_PASSED,
                    false,
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    MESSAGE_PICKUP_PASSED,
                    adminAllowed,
                    false,
                    null);
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            if (createdWithinFirstHour(booking, now) || booking.getPickupDateTime().isAfter(now.plusHours(24))) {
                return new CancellationPolicyDecision(
                        true,
                        null,
                        true,
                        money(booking.getTotalPrice()),
                        money(BigDecimal.ZERO),
                        MESSAGE_FULL_REFUND,
                        true,
                        false,
                        null);
            }

            boolean adminAllowed = admin && canTransitionToCancelled(booking);
            return new CancellationPolicyDecision(
                    adminAllowed,
                    adminAllowed ? null : REASON_WINDOW_EXPIRED,
                    false,
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    MESSAGE_WINDOW_EXPIRED,
                    adminAllowed,
                    false,
                    null);
        }

        if (booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.FAILED) {
            return new CancellationPolicyDecision(
                    true,
                    null,
                    false,
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    MESSAGE_NO_CHARGE,
                    true,
                    false,
                    null);
        }

        return blocked("Booking cannot be cancelled.", "This booking cannot be cancelled.", false);
    }

    private boolean createdWithinFirstHour(Booking booking, LocalDateTime now) {
        if (booking.getCreatedAt() == null) {
            return false;
        }
        LocalDateTime createdAt = LocalDateTime.ofInstant(booking.getCreatedAt(), businessTimezone.zone());
        return !createdAt.plusHours(1).isBefore(now);
    }

    private boolean canTransitionToCancelled(Booking booking) {
        return booking.getStatus() == BookingStatus.PENDING
                || booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.FAILED;
    }

    private CancellationPolicyDecision blocked(String reason, String message, boolean adminAllowed) {
        return new CancellationPolicyDecision(
                false,
                reason,
                false,
                money(BigDecimal.ZERO),
                money(BigDecimal.ZERO),
                message,
                adminAllowed,
                false,
                null);
    }

    private BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }
}
