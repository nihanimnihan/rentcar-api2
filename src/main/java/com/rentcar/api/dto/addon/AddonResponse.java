package com.rentcar.api.dto.addon;

import java.math.BigDecimal;

public record AddonResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String pricingType,
        boolean active
) {
}
