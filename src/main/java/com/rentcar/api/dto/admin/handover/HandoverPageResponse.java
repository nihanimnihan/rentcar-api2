package com.rentcar.api.dto.admin.handover;

import com.rentcar.api.dto.admin.AdminBookingDetailedListItem;

import java.util.List;

public record HandoverPageResponse(
        AdminBookingDetailedListItem booking,
        BookingDepositResponse deposit,
        VehicleHandoverResponse handover,
        List<VehicleDamageResponse> damages,
        boolean canStartHandover,
        boolean canMarkPickedUp
) {}
