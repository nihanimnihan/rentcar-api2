package com.rentcar.api.domain.booking;

/**
 * Represents the pricing/flexibility tier chosen at booking time.
 *
 * <p>Currently only BEST_PRICE is active (default for all rental bookings).
 *
 * <p>TODO: STAY_FLEXIBLE will later:
 * <ul>
 *   <li>add a daily flexibility fee (stored in {@code Booking.bookingOptionDailyFee})</li>
 *   <li>apply {@link CancellationPolicyType#FREE_CANCELLATION} — free cancellation / rebooking until pickup</li>
 *   <li>surface the fee in {@code PriceBreakdown} and the booking confirmation UI</li>
 * </ul>
 */
public enum BookingOptionType {

    /**
     * Standard rate with the lowest price.
     * Cancellation policy follows {@link CancellationPolicyType#STRICT}.
     */
    BEST_PRICE,

    /**
     * Adds a daily flexibility fee on top of the base rate.
     * Grants free cancellation or date change up until the pickup time.
     * Not yet active — fee calculation and policy enforcement are deferred.
     */
    STAY_FLEXIBLE
}
