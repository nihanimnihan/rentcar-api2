package com.rentcar.api.util;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Single source of truth for UTC timestamps.
 *
 * Wraps a Spring-managed {@link Clock} so that every call to "now" can be
 * controlled in tests by substituting a {@code Clock.fixed(...)} bean.
 *
 * Use this for:
 * - Audit timestamps (createdAt, updatedAt, paidAt)
 * - Any non-business-local "now" comparison
 *
 * Do NOT use this for pickup/dropoff date comparisons — use {@link BusinessTimezone} instead.
 */
@Component
public class AppClock {

    private final Clock clock;

    public AppClock(Clock clock) {
        this.clock = clock;
    }

    /** Current UTC instant. Replaces {@code Instant.now()} everywhere in application code. */
    public Instant nowUtc() {
        return Instant.now(clock);
    }

    /** Exposes the underlying clock for use in {@link BusinessTimezone} and JPA auditing. */
    public Clock clock() {
        return clock;
    }
}
