package com.rentcar.api.dto.pricing;

import java.math.BigDecimal;

public record AddonPriceLine(
        String code,
        String name,
        BigDecimal unitPrice,
        String pricingType,
        int quantity,
        BigDecimal totalPrice
) {
}
