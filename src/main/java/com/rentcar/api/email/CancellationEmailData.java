package com.rentcar.api.email;

import com.rentcar.api.domain.payment.PaymentStatus;

/**
 * Immutable data bag for a booking cancellation email.
 */
public record CancellationEmailData(
        String bookingReference,
        String customerEmail,
        String customerName,
        String cancellationReason,
        PaymentStatus refundStatus,
        /** Optional manage-booking deep-link. Null if base URL not configured. */
        String managementUrl,
        String language
) {}
