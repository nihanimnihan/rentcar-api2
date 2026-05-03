package com.rentcar.api.dto.car;

import java.math.BigDecimal;

public record CarResponse (
        Long id,
        String brand,
        String model,
        String segment,
        BigDecimal dailyPrice,
        String imageUrl
) {
}