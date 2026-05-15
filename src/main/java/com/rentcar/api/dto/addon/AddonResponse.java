package com.rentcar.api.dto.addon;

import java.math.BigDecimal;

public record AddonResponse(
        Long id,
        String name,
        String nameEs,
        String code,
        String description,
        String descriptionEs,
        BigDecimal price,
        String pricingType,
        String imageUrl,
        boolean recommended,
        boolean active
) {
}
