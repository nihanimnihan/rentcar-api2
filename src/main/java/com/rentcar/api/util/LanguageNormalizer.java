package com.rentcar.api.util;

import java.util.Locale;

public final class LanguageNormalizer {

    public static final String DEFAULT_LANGUAGE = "en";

    private LanguageNormalizer() {
    }

    public static String normalizeOrDefault(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("tr")) {
            return "tr";
        }
        if (normalized.startsWith("es")) {
            return "es";
        }
        return DEFAULT_LANGUAGE;
    }
}
