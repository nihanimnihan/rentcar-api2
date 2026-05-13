package com.rentcar.api.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single source of truth for timezone handling.
 *
 * - pickupDateTime / dropoffDateTime are wall-clock times at the rental location (Europe/Madrid).
 * - All "now" comparisons for those fields must use {@link #nowBusiness()}.
 * - Audit timestamps (createdAt, paidAt, etc.) use {@link java.time.Instant} — absolute UTC moments.
 * - JVM is pinned to UTC in {@code RentcarApiApplication.main()} to keep LocalDateTime.now() deterministic.
 */
public final class AppTimezone {

    /** The timezone where the rental business operates. All pickup/dropoff times are in this zone. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Madrid");

    private AppTimezone() {}

    /**
     * Returns the current wall-clock time in the business timezone.
     * Use this when validating pickup/dropoff dates, NOT {@code LocalDateTime.now()}.
     */
    public static LocalDateTime nowBusiness() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }
}
