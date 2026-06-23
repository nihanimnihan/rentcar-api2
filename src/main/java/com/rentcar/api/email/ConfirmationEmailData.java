package com.rentcar.api.email;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable data bag for a booking confirmation email.
 *
 * All fields map directly to the confirmation email template content.
 * {@code managementUrl} is nullable — omit from the rendered email if null/blank.
 */
public record ConfirmationEmailData(
        String bookingReference,
        String customerEmail,
        String customerName,
        LocalDateTime pickupDateTime,
        String pickupLocation,
        LocalDateTime dropoffDateTime,
        String dropoffLocation,
        String selectedService,
        String insuranceName,
        BigDecimal insuranceTotal,
        BigDecimal depositAmount,
        BigDecimal totalPrice,
        /** Optional manage-booking deep-link. Null if base URL not configured. */
        String managementUrl,
        String language
) {}
