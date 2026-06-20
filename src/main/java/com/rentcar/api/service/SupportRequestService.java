package com.rentcar.api.service;

import com.rentcar.api.domain.support.SupportRequest;
import com.rentcar.api.domain.support.SupportRequestStatus;
import com.rentcar.api.dto.support.CreateSupportRequestRequest;
import com.rentcar.api.dto.support.SupportRequestResponse;
import com.rentcar.api.repository.SupportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportRequestService {

    private final SupportRequestRepository supportRequestRepository;

    @Transactional
    public SupportRequestResponse create(CreateSupportRequestRequest request) {
        SupportRequest supportRequest = SupportRequest.builder()
                .topic(request.topic())
                .bookingReference(blankToNull(request.bookingReference()))
                .email(request.email().trim())
                .message(request.message().trim())
                .status(SupportRequestStatus.OPEN)
                .build();

        return toResponse(supportRequestRepository.save(supportRequest));
    }

    private SupportRequestResponse toResponse(SupportRequest supportRequest) {
        return new SupportRequestResponse(
                supportRequest.getId(),
                supportRequest.getTopic(),
                supportRequest.getBookingReference(),
                supportRequest.getEmail(),
                supportRequest.getMessage(),
                supportRequest.getStatus(),
                supportRequest.getCreatedAt(),
                supportRequest.getUpdatedAt()
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
