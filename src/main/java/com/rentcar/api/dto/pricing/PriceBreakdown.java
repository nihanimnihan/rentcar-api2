package com.rentcar.api.dto.pricing;

import java.math.BigDecimal;
import java.util.List;

public record PriceBreakdown(
        int rentalDays,
        BigDecimal baseDailyPrice,
        BigDecimal effectiveDailyPrice,
        BigDecimal discountPercentage,
        BigDecimal rentalCharge,
        BigDecimal oneWayFee,
        BigDecimal premiumLocationFee,
        BigDecimal tax,
        BigDecimal totalPrice,
        BigDecimal addonsTotal,
        List<AddonPriceLine> addonLines,
        int includedKm,
        BigDecimal unlimitedKmDailyPrice
) {
}