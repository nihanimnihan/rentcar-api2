package com.rentcar.api.dto.transfer;

import java.math.BigDecimal;

public record ChauffeurCategoryOfferResponse(
        Long categoryId,
        String code,
        String name,
        String description,
        int seats,
        int bags,
        boolean electric,
        String imageUrl,
        BigDecimal hourlyPriceFrom,
        BigDecimal totalPrice,
        boolean available) {
}
