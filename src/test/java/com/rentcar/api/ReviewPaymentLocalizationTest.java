package com.rentcar.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewPaymentLocalizationTest {

    private static final Pattern TRANSLATION_KEY_PATTERN =
            Pattern.compile("['\"]([^'\"]+)['\"]\\s*:");

    @Test
    void paymentStartFailureModalUsesTranslationKeys() throws Exception {
        String reviewJs = Files.readString(Path.of("src/main/resources/static/js/review.js"));

        assertThat(reviewJs)
                .contains("t('review.paymentStartFailedTitle')")
                .contains("t('review.paymentStartFailedMessage')")
                .contains("t('review.paymentUnavailableMessage')")
                .contains("t('review.tryAgain')")
                .doesNotContain("title: \"Payment could not be started\"");
    }

    @Test
    void stripeCardValidationUsesInlineFieldErrors() throws Exception {
        String reviewJs = Files.readString(Path.of("src/main/resources/static/js/review.js"));

        assertThat(reviewJs)
                .contains("showStripeCardValidation(result.error.message || t('review.cardDetailsIncomplete'))")
                .contains("isStripeValidationError(result.error)")
                .contains("setStripeCardError(event.error?.message || \"\")")
                .contains("stripeCard.focus()")
                .doesNotContain("Payment failed. Please check your card details or try another card.");
    }

    @Test
    void paymentStartFailureTranslationsExistForSupportedLanguages() throws Exception {
        for (String language : List.of("en", "es", "tr")) {
            String translations = Files.readString(Path.of("src/main/resources/static/js/i18n/" + language + ".js"));

            assertThat(translations)
                    .as(language + " payment-start failure translations")
                    .contains("'review.paymentStartFailedTitle'")
                    .contains("'review.paymentStartFailedMessage'")
                    .contains("'review.paymentUnavailableMessage'")
                    .contains("'review.tryAgain'");
        }
    }

    @Test
    void supportedLanguagesContainEveryEnglishTranslationKey() throws Exception {
        Set<String> englishKeys = translationKeys("en");

        for (String language : List.of("es", "tr")) {
            Set<String> localizedKeys = translationKeys(language);

            assertThat(localizedKeys)
                    .as(language + " must contain every key from en.js")
                    .containsAll(englishKeys);
        }
    }

    private Set<String> translationKeys(String language) throws Exception {
        String translations = Files.readString(Path.of("src/main/resources/static/js/i18n/" + language + ".js"));
        Matcher matcher = TRANSLATION_KEY_PATTERN.matcher(translations);
        Set<String> keys = new LinkedHashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
