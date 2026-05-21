package com.rentcar.api.domain.booking;

/**
 * Cancellation/rebooking policy associated with a booking option.
 *
 * <p>TODO: Enforcement is deferred until STAY_FLEXIBLE is fully implemented.
 * When active, {@code STRICT} bookings will reject cancellation/date-change
 * requests after a configurable cut-off, while {@code FREE_CANCELLATION}
 * bookings will allow changes until the pickup time at no charge.
 */
public enum CancellationPolicyType {

    /**
     * No free cancellation. Applies to {@link BookingOptionType#BEST_PRICE} bookings.
     */
    STRICT,

    /**
     * Free cancellation or rebooking up to the pickup date/time.
     * Applies to {@link BookingOptionType#STAY_FLEXIBLE} bookings.
     */
    FREE_CANCELLATION
}
