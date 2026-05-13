package com.rentcar.api.dto.addon;

import com.rentcar.api.domain.addon.AddonPricingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateAddonRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotNull AddonPricingType pricingType,
        boolean active
) {
}
