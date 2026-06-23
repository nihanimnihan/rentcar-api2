package com.rentcar.api.dto.insurance;

public record InsuranceCoverageItemResponse(
        Long id,
        String title,
        String description,
        boolean included,
        int displayOrder
) {
}
