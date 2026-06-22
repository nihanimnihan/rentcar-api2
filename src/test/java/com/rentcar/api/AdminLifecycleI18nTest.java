package com.rentcar.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLifecycleI18nTest {

    private static final List<String> REQUIRED_ADMIN_KEYS = List.of(
            "admin.detail.lifecycle",
            "admin.detail.cancellationStatus",
            "admin.detail.cancellationEligibility",
            "admin.detail.refundEligibility",
            "admin.detail.refundAmount",
            "admin.detail.cancellationPolicyExplanation",
            "admin.payment.NO_REFUND",
            "admin.lifecycle.cancelable",
            "admin.lifecycle.notCancelable",
            "admin.lifecycle.refundEligible",
            "admin.lifecycle.notRefundable",
            "admin.lifecycle.noShow",
            "admin.lifecycle.cancelBooking",
            "admin.lifecycle.cancelRefund",
            "admin.lifecycle.markNoShow",
            "admin.lifecycle.processing",
            "admin.lifecycle.actionError"
    );

    private static final List<String> REQUIRED_CUSTOMER_KEYS = List.of(
            "manage.policyWindowExpiredReason",
            "manage.policyWindowExpired"
    );

    @Test
    void adminLifecycleKeysExistInAllAdminLanguages() throws Exception {
        String adminI18n = Files.readString(Path.of("src/main/resources/static/js/admin-i18n.js"));
        for (String key : REQUIRED_ADMIN_KEYS) {
            assertThat(countOccurrences(adminI18n, "'" + key + "'"))
                    .as("admin key %s should exist for en, es and tr", key)
                    .isEqualTo(3);
        }
    }

    @Test
    void customerPolicyKeysExistInAllCustomerLanguages() throws Exception {
        for (String language : List.of("en", "es", "tr")) {
            String i18n = Files.readString(Path.of("src/main/resources/static/js/i18n/" + language + ".js"));
            for (String key : REQUIRED_CUSTOMER_KEYS) {
                assertThat(i18n)
                        .as("%s should contain %s", language, key)
                        .contains("'" + key + "'");
            }
        }
    }

    private long countOccurrences(String text, String needle) {
        long count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
