package com.rentcar.api.dto.insurance;

import java.math.BigDecimal;
import java.util.List;

public record InsurancePackageResponse(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal pricePerDay,
        BigDecimal depositAmount,
        int displayOrder,
        boolean recommended,
        String badge,
        List<InsuranceCoverageItemResponse> coverageItems
) {
}
