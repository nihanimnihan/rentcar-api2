package com.rentcar.api.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Single source of truth for business-local time.
 *
 * All pickup/dropoff datetimes are wall-clock times at the rental location
 * (Europe/Madrid). Comparing them against "now" must use this class, not the
 * JVM default or {@code LocalDateTime.now()}, because:
 *
 *   - JVM is pinned to UTC.
 *   - Business operates in Europe/Madrid (UTC+1 winter, UTC+2 summer/CEST).
 *   - A pickup stored as {@code 2026-06-15T10:00} means 10:00 Madrid time;
 *     comparing it against UTC "now" introduces an offset error up to 2 hours.
 *
 * The underlying {@link AppClock} is injectable, so tests can pass a
 * {@code Clock.fixed(...)} to make all comparisons deterministic.
 */
@Component
public class BusinessTimezone {

    /** All user-facing rental datetimes are in this zone. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private final AppClock appClock;

    public BusinessTimezone(AppClock appClock) {
        this.appClock = appClock;
    }

    /** The business timezone — Europe/Madrid. */
    public ZoneId zone() {
        return ZONE;
    }

    /** Current moment expressed in the business timezone (timezone-aware). */
    public ZonedDateTime nowBusiness() {
        return ZonedDateTime.now(appClock.clock().withZone(ZONE));
    }

    /**
     * Current business date as a {@link LocalDateTime} stripped of timezone info.
     *
     * <p>Use this when comparing pickup/dropoff {@code LocalDateTime} fields directly:
     * <pre>
     *     request.pickupDateTime().isBefore(businessTimezone.nowBusinessLocal().plusHours(1))
     * </pre>
     */
    public LocalDateTime nowBusinessLocal() {
        return nowBusiness().toLocalDateTime();
    }

    /** Today's date in the business timezone. Useful for same-day rental checks. */
    public LocalDate todayBusiness() {
        return LocalDate.now(appClock.clock().withZone(ZONE));
    }
}
