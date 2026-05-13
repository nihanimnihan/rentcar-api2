package com.rentcar.api.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Duration-based discount tiers and premium location configuration loaded from application properties.
 *
 * Discount tiers: each tier defines the minimum rental day count that qualifies for that discount.
 * The service selects the tier with the highest minDays that is still <= rentalDays.
 *
 * Premium locations: an explicit closed set of location names that carry a premium surcharge.
 * Matching is case-insensitive exact match (not substring) to prevent false positives
 * (e.g. "Hotel District 2" matching "t2") and fee evasion via renamed inputs.
 *
 * Both can be adjusted per environment without a code change.
 */
@Validated
@ConfigurationProperties(prefix = "rentcar.pricing")
public record PricingProperties(

        @NotEmpty
        List<DiscountTier> discountTiers,

        @NotEmpty
        List<String> premiumLocations

) {

    /**
     * Returns a normalized (trimmed, lowercased) set of premium location names.
     * Built once per call — callers should cache the result or call via PricingService.
     */
    public Set<String> normalizedPremiumLocations() {
        return premiumLocations.stream()
                .map(l -> l.trim().toLowerCase())
                .collect(Collectors.toSet());
    }

    public record DiscountTier(
            int minDays,
            BigDecimal discountPercentage
    ) {
    }
}
