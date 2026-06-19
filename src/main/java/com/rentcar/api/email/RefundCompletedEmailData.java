package com.rentcar.api.email;

/**
 * Immutable data bag for a refund-completed email.
 */
public record RefundCompletedEmailData(
        String bookingReference,
        String customerEmail,
        String customerName,
        String refundReference,
        String bankProcessingMessage
) {}
