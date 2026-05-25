package com.rentcar.api.dto.admin;

import com.rentcar.api.domain.addon.AddonPricingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminAddonRequest(
        @NotBlank String name,
        String nameEs,
        @NotBlank String code,
        String description,
        String descriptionEs,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotNull AddonPricingType pricingType,
        String imageUrl,
        boolean recommended,
        boolean active
) {
}
