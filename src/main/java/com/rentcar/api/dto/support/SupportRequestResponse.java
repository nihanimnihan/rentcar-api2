package com.rentcar.api.dto.support;

import com.rentcar.api.domain.support.SupportRequestStatus;
import com.rentcar.api.domain.support.SupportRequestTopic;

import java.time.Instant;

public record SupportRequestResponse(
        Long id,
        SupportRequestTopic topic,
        String bookingReference,
        String email,
        String message,
        SupportRequestStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
