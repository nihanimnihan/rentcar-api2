package com.rentcar.api.dto.pricing;

import java.math.BigDecimal;

public record PriceBreakdown(
        int rentalDays,
        BigDecimal baseDailyPrice,
        BigDecimal discountedDailyPrice,
        BigDecimal rentalCharge,
        BigDecimal oneWayFee,
        BigDecimal premiumLocationFee,
        BigDecimal tax,
        BigDecimal totalPrice
) {
}