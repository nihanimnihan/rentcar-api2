package com.rentcar.api.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BookingReferenceGenerator.
 *
 * Uses a fixed clock pinned to 2026-05-21T12:00:00Z (= 14:00 Madrid summer time)
 * so the date portion in references is deterministic.
 */
class BookingReferenceGeneratorTest {

    /** 2026-05-21 12:00 UTC = 14:00 Madrid (CEST, UTC+2) → business date 2026-05-21 → "260521". */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-21T12:00:00Z"),
            ZoneId.of("UTC")
    );

    private BookingReferenceGenerator newGenerator() {
        AppClock appClock = new AppClock(FIXED_CLOCK);
        BusinessTimezone tz = new BusinessTimezone(appClock);
        return new BookingReferenceGenerator(tz);
    }

    @Test
    void generate_formatMatchesRcYymmddXxxxPattern() {
        String ref = newGenerator().generate();
        assertThat(ref).matches("RC-\\d{6}-[A-Z0-9]{4}");
    }

    @Test
    void generate_datePartReflectsBusinessDate() {
        String ref = newGenerator().generate();
        // Fixed UTC noon on 2026-05-21 = 14:00 Madrid → same calendar date
        assertThat(ref).startsWith("RC-260521-");
    }

    @Test
    void generate_suffixHasCorrectLength() {
        String ref = newGenerator().generate();
        String suffix = ref.substring(ref.lastIndexOf('-') + 1);
        assertThat(suffix).hasSize(BookingReferenceGenerator.SUFFIX_LENGTH);
    }

    @Test
    void generate_suffixContainsNoConfusableChars() {
        BookingReferenceGenerator gen = newGenerator();
        for (int i = 0; i < 200; i++) {
            String suffix = gen.generate().substring(gen.generate().lastIndexOf('-') + 1);
            assertThat(suffix)
                    .as("suffix must not contain O, I, 0, or 1")
                    .doesNotContain("O", "I", "0", "1");
        }
    }

    @Test
    void generate_allSuffixCharsAreInAlphabet() {
        BookingReferenceGenerator gen = newGenerator();
        Set<Character> allowed = new HashSet<>();
        for (char c : BookingReferenceGenerator.ALPHABET.toCharArray()) {
            allowed.add(c);
        }
        for (int i = 0; i < 100; i++) {
            String ref = gen.generate();
            String suffix = ref.substring(ref.lastIndexOf('-') + 1);
            for (char c : suffix.toCharArray()) {
                assertThat(allowed).as("char '%s' not in allowed alphabet", c).contains(c);
            }
        }
    }

    @Test
    void generate_producesStatisticallyUniqueValues() {
        BookingReferenceGenerator gen = newGenerator();
        Set<String> refs = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            refs.add(gen.generate());
        }
        // With 32^4 ≈ 1M combinations, 50 draws should always be unique
        assertThat(refs).hasSize(50);
    }
}
