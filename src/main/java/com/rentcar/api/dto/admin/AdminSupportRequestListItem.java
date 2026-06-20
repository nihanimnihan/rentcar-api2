package com.rentcar.api.dto.admin;

import com.rentcar.api.domain.support.SupportRequestStatus;
import com.rentcar.api.domain.support.SupportRequestTopic;

import java.time.Instant;

public record AdminSupportRequestListItem(
        Long id,
        SupportRequestTopic topic,
        String email,
        String phoneCountryCode,
        String phoneNumber,
        String fullPhone,
        String bookingReference,
        SupportRequestStatus status,
        Instant createdAt
) {
}
