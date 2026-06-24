package com.rentcar.api.dto.admin.handover;

import com.rentcar.api.domain.handover.BookingDepositMethod;
import jakarta.validation.constraints.NotNull;

public record ManualDepositCollectionRequest(
        @NotNull BookingDepositMethod method,
        String note
) {}
