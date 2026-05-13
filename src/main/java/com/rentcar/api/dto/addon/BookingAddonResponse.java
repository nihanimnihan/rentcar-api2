package com.rentcar.api.dto.addon;

import java.math.BigDecimal;

public record BookingAddonResponse(
        Long addonId,
        String name,
        String pricingTypeSnapshot,
        BigDecimal lineTotal
) {
}
