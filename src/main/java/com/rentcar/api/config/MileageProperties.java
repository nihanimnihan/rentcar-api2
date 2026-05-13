package com.rentcar.api.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Configuration for included-km and unlimited-km pricing.
 *
 * Formula for included km:
 *   days <= baseDayThreshold  → days * dailyKmBase
 *   days >  baseDayThreshold  → (baseDayThreshold * dailyKmBase) + ((days - baseDayThreshold) * kmPerExtraDay)
 *
 * Formula for unlimited km daily price:
 *   max(effectiveDailyPrice * unlimitedKmPercentage, unlimitedKmMinDailyPrice)
 */
@Validated
@ConfigurationProperties(prefix = "rentcar.mileage")
public record MileageProperties(

        @Min(1)
        int dailyKmBase,

        @Min(1)
        int kmPerExtraDay,

        @Min(1)
        int baseDayThreshold,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal unlimitedKmPercentage,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal unlimitedKmMinDailyPrice

) {
}
