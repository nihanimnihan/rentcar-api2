package com.rentcar.api.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utility for normalizing customer-facing name strings for accent-insensitive comparison.
 *
 * <p>Rules applied by {@link #normalize(String)}:
 * <ol>
 *   <li>Trim surrounding whitespace.</li>
 *   <li>Collapse internal runs of whitespace to a single space.</li>
 *   <li>NFD decomposition (e.g. ü → u + combining diaeresis).</li>
 *   <li>Remove all combining diacritical marks.</li>
 *   <li>Lowercase using {@link Locale#ROOT} (locale-independent).</li>
 * </ol>
 *
 * <p>This makes "Güner", "Guner", "GUNER" and "  guner  " all equal.
 *
 * <p>All methods are static and this class is not instantiable.
 */
public final class NameNormalizer {

    private NameNormalizer() {}

    /**
     * Returns a normalized form suitable for accent-insensitive comparison.
     * Returns an empty string for null or blank input.
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) return "";
        // Collapse whitespace first so NFD doesn't interact with it.
        String collapsed = input.trim().replaceAll("\\s+", " ");
        String nfd = Normalizer.normalize(collapsed, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}", "")
                  .toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the last whitespace-delimited token of a full name (i.e. the family name).
     * "Nihan Güner" → "Güner", "Doe" → "Doe", null/blank → "".
     *
     * <p>This extracts the <em>display</em> last name; call {@link #normalize(String)}
     * on the result if you need a comparison-safe form.
     */
    public static String extractLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }
}
