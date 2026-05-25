package com.rentcar.api.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminAddonResponse(
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
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
