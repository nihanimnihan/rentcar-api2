package com.rentcar.api.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

/**
 * Duration-based discount tiers loaded from application properties.
 *
 * Each tier defines the minimum rental day count that qualifies for that discount.
 * The service selects the tier with the highest minDays that is still <= rentalDays.
 *
 * Tiers are configured under rentcar.pricing.discount-tiers and can be adjusted
 * per environment without a code change. Move to a DB table when admin-controlled
 * pricing is needed.
 */
@Validated
@ConfigurationProperties(prefix = "rentcar.pricing")
public record PricingProperties(

        @NotEmpty
        List<DiscountTier> discountTiers

) {

    public record DiscountTier(
            int minDays,
            BigDecimal discountPercentage
    ) {
    }
}
