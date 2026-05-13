package com.rentcar.api.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BusinessTimezone.
 *
 * Fixed clock: 2026-06-15T08:00:00Z (UTC)
 * Europe/Madrid in CEST (summer) is UTC+2, so business time = 10:00.
 *
 * All assertions use the fixed clock — zero dependency on the real wall clock.
 */
class BusinessTimezoneTest {

    // 2026-06-15 08:00 UTC = 2026-06-15 10:00 Europe/Madrid (CEST = UTC+2)
    private static final Instant FIXED_UTC = Instant.parse("2026-06-15T08:00:00Z");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private BusinessTimezone businessTimezone;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_UTC, MADRID);
        AppClock appClock = new AppClock(fixedClock);
        businessTimezone = new BusinessTimezone(appClock);
    }

    @Test
    @DisplayName("zone() returns Europe/Madrid")
    void zone_returnsEuropeMadrid() {
        assertThat(businessTimezone.zone()).isEqualTo(MADRID);
    }

    @Test
    @DisplayName("nowBusiness() returns the current moment in Europe/Madrid timezone")
    void nowBusiness_returnsCorrectMadridTime() {
        ZonedDateTime result = businessTimezone.nowBusiness();

        assertThat(result.getZone()).isEqualTo(MADRID);
        // 08:00 UTC = 10:00 CEST
        assertThat(result.getHour()).isEqualTo(10);
        assertThat(result.getMinute()).isEqualTo(0);
    }

    @Test
    @DisplayName("nowBusinessLocal() returns LocalDateTime stripped of timezone — same wall-clock hour")
    void nowBusinessLocal_returnsLocalDateTimeAt10() {
        LocalDateTime result = businessTimezone.nowBusinessLocal();

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 6, 15, 10, 0, 0));
    }

    @Test
    @DisplayName("todayBusiness() returns today's date in Europe/Madrid")
    void todayBusiness_returnsMadridDate() {
        LocalDate result = businessTimezone.todayBusiness();

        assertThat(result).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("pickup at 11:00 Madrid is AFTER business now (10:00) — valid future booking")
    void pickup_oneHourFromNow_isInFuture() {
        // User selects pickup at 11:00 Madrid (1 hour from our fixed clock)
        LocalDateTime pickup = LocalDateTime.of(2026, 6, 15, 11, 0);
        LocalDateTime businessNowPlusOneHour = businessTimezone.nowBusinessLocal().plusHours(1);

        // 11:00 is NOT before 10:00+1h = 11:00 — on the boundary, so isBefore is false
        assertThat(pickup.isBefore(businessNowPlusOneHour)).isFalse();
    }

    @Test
    @DisplayName("pickup at 10:30 Madrid is within 1 hour of now (10:00) — must be rejected")
    void pickup_thirtyMinutesFromNow_isTooSoon() {
        // User selects pickup at 10:30 Madrid (only 30 min from our fixed clock at 10:00)
        LocalDateTime pickup = LocalDateTime.of(2026, 6, 15, 10, 30);
        LocalDateTime businessNowPlusOneHour = businessTimezone.nowBusinessLocal().plusHours(1);

        // 10:30 IS before 11:00 (1h from now) — should be rejected
        assertThat(pickup.isBefore(businessNowPlusOneHour)).isTrue();
    }

    @Test
    @DisplayName("pickup in the past (09:00 Madrid) is before business now — must be rejected")
    void pickup_inThePast_isBeforeNow() {
        LocalDateTime pastPickup = LocalDateTime.of(2026, 6, 15, 9, 0);

        assertThat(pastPickup.isBefore(businessTimezone.nowBusinessLocal())).isTrue();
    }

    @Test
    @DisplayName("nowBusinessLocal() is deterministic — repeated calls return same value with fixed clock")
    void nowBusinessLocal_withFixedClock_isDeterministic() {
        LocalDateTime first = businessTimezone.nowBusinessLocal();
        LocalDateTime second = businessTimezone.nowBusinessLocal();

        assertThat(first).isEqualTo(second);
    }
}
