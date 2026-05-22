package com.rentcar.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameNormalizerTest {

    // ── normalize() ──────────────────────────────────────────────────────────

    @Test
    void normalize_removesAccents() {
        assertThat(NameNormalizer.normalize("Güner")).isEqualTo("guner");
    }

    @Test
    void normalize_lowercases() {
        assertThat(NameNormalizer.normalize("GUNER")).isEqualTo("guner");
    }

    @Test
    void normalize_accentAndCase() {
        assertThat(NameNormalizer.normalize("GÜNER")).isEqualTo("guner");
    }

    @Test
    void normalize_trims() {
        assertThat(NameNormalizer.normalize("  Guner  ")).isEqualTo("guner");
    }

    @Test
    void normalize_collapsesInternalSpaces() {
        assertThat(NameNormalizer.normalize("Nihan  Güner")).isEqualTo("nihan guner");
    }

    @Test
    void normalize_frenchAccent() {
        assertThat(NameNormalizer.normalize("François")).isEqualTo("francois");
    }

    @Test
    void normalize_spanishAccent() {
        assertThat(NameNormalizer.normalize("José")).isEqualTo("jose");
    }

    @Test
    void normalize_null_returnsEmpty() {
        assertThat(NameNormalizer.normalize(null)).isEqualTo("");
    }

    @Test
    void normalize_blank_returnsEmpty() {
        assertThat(NameNormalizer.normalize("   ")).isEqualTo("");
    }

    @Test
    void normalize_accentedAndNonAccented_compareEqual() {
        // The core contract: "Güner" and "Guner" must normalize to the same value.
        assertThat(NameNormalizer.normalize("Güner"))
                .isEqualTo(NameNormalizer.normalize("Guner"));
    }

    // ── extractLastName() ────────────────────────────────────────────────────

    @Test
    void extractLastName_singleToken() {
        assertThat(NameNormalizer.extractLastName("Doe")).isEqualTo("Doe");
    }

    @Test
    void extractLastName_twoTokens() {
        assertThat(NameNormalizer.extractLastName("Nihan Güner")).isEqualTo("Güner");
    }

    @Test
    void extractLastName_threeTokens() {
        assertThat(NameNormalizer.extractLastName("Maria José García")).isEqualTo("García");
    }

    @Test
    void extractLastName_extraSpaces() {
        // Trim + split on any whitespace
        assertThat(NameNormalizer.extractLastName("  Nihan   Güner  ")).isEqualTo("Güner");
    }

    @Test
    void extractLastName_null_returnsEmpty() {
        assertThat(NameNormalizer.extractLastName(null)).isEqualTo("");
    }

    @Test
    void extractLastName_blank_returnsEmpty() {
        assertThat(NameNormalizer.extractLastName("  ")).isEqualTo("");
    }

    // ── combined: normalized last name comparison ────────────────────────────

    @Test
    void normalizedLastName_accentedStoredMatchesPlainInput() {
        String stored = NameNormalizer.normalize(NameNormalizer.extractLastName("Nihan Güner"));
        String input  = NameNormalizer.normalize("Guner");
        assertThat(stored).isEqualTo(input);
    }

    @Test
    void normalizedLastName_plainStoredMatchesAccentedInput() {
        String stored = NameNormalizer.normalize(NameNormalizer.extractLastName("Nihan Guner"));
        String input  = NameNormalizer.normalize("Güner");
        assertThat(stored).isEqualTo(input);
    }

    @Test
    void normalizedLastName_extraSpacesInInput_matchStored() {
        String stored = NameNormalizer.normalize(NameNormalizer.extractLastName("Nihan Güner"));
        String input  = NameNormalizer.normalize("  Güner  ");
        assertThat(stored).isEqualTo(input);
    }
}
