package com.rentcar.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AppClock.
 * No Spring context — Clock is injected directly.
 */
class AppClockTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-15T08:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Test
    @DisplayName("nowUtc() returns the instant from the injected clock, not the real wall clock")
    void nowUtc_returnsInjectedClockInstant() {
        AppClock appClock = new AppClock(FIXED_CLOCK);

        assertThat(appClock.nowUtc()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    @DisplayName("clock() exposes the underlying Clock for downstream use")
    void clock_returnsUnderlyingClock() {
        AppClock appClock = new AppClock(FIXED_CLOCK);

        assertThat(appClock.clock()).isSameAs(FIXED_CLOCK);
    }

    @Test
    @DisplayName("successive nowUtc() calls return the same instant when clock is fixed")
    void nowUtc_withFixedClock_isDeterministic() {
        AppClock appClock = new AppClock(FIXED_CLOCK);

        Instant first = appClock.nowUtc();
        Instant second = appClock.nowUtc();

        assertThat(first).isEqualTo(second);
    }
}
