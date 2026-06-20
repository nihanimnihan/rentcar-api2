package com.rentcar.api.service;

import com.rentcar.api.domain.support.SupportRequest;
import com.rentcar.api.domain.support.SupportRequestStatus;
import com.rentcar.api.dto.admin.AdminSupportRequestPage;
import com.rentcar.api.dto.admin.AdminSupportRequestDetail;
import com.rentcar.api.dto.admin.AdminSupportRequestListItem;
import com.rentcar.api.dto.support.CreateSupportRequestRequest;
import com.rentcar.api.dto.support.SupportRequestResponse;
import com.rentcar.api.repository.SupportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
                .phoneCountryCode(request.phoneCountryCode().trim())
                .phoneNumber(request.phoneNumber().trim())
                .message(request.message().trim())
                .status(SupportRequestStatus.OPEN)
                .build();

        return toResponse(supportRequestRepository.save(supportRequest));
    }

    @Transactional(readOnly = true)
    public AdminSupportRequestPage listAdminRequests(int page, int size, SupportRequestStatus status) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<SupportRequest> requests = supportRequestRepository.findAdminPage(status, PageRequest.of(safePage, safeSize));
        return new AdminSupportRequestPage(
                requests.getContent().stream().map(this::toAdminListItem).toList(),
                requests.getNumber(),
                requests.getSize(),
                requests.getTotalElements(),
                requests.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AdminSupportRequestDetail getAdminRequest(Long id) {
        return toAdminDetail(findById(id));
    }

    @Transactional
    public AdminSupportRequestDetail resolveAdminRequest(Long id) {
        SupportRequest supportRequest = findById(id);
        if (supportRequest.getStatus() == SupportRequestStatus.OPEN) {
            supportRequest.setStatus(SupportRequestStatus.RESOLVED);
            supportRequest = supportRequestRepository.save(supportRequest);
        }
        return toAdminDetail(supportRequest);
    }

    private SupportRequestResponse toResponse(SupportRequest supportRequest) {
        return new SupportRequestResponse(
                supportRequest.getId(),
                supportRequest.getTopic(),
                supportRequest.getBookingReference(),
                supportRequest.getEmail(),
                supportRequest.getPhoneCountryCode(),
                supportRequest.getPhoneNumber(),
                supportRequest.getMessage(),
                supportRequest.getStatus(),
                supportRequest.getCreatedAt(),
                supportRequest.getUpdatedAt()
        );
    }

    private AdminSupportRequestListItem toAdminListItem(SupportRequest supportRequest) {
        return new AdminSupportRequestListItem(
                supportRequest.getId(),
                supportRequest.getTopic(),
                supportRequest.getEmail(),
                supportRequest.getPhoneCountryCode(),
                supportRequest.getPhoneNumber(),
                fullPhone(supportRequest),
                supportRequest.getBookingReference(),
                supportRequest.getStatus(),
                supportRequest.getCreatedAt()
        );
    }

    private AdminSupportRequestDetail toAdminDetail(SupportRequest supportRequest) {
        return new AdminSupportRequestDetail(
                supportRequest.getId(),
                supportRequest.getTopic(),
                supportRequest.getEmail(),
                supportRequest.getPhoneCountryCode(),
                supportRequest.getPhoneNumber(),
                fullPhone(supportRequest),
                supportRequest.getBookingReference(),
                supportRequest.getMessage(),
                supportRequest.getStatus(),
                supportRequest.getCreatedAt(),
                supportRequest.getUpdatedAt()
        );
    }

    private SupportRequest findById(Long id) {
        return supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support request not found"));
    }

    private String fullPhone(SupportRequest supportRequest) {
        if (supportRequest.getPhoneCountryCode() == null || supportRequest.getPhoneNumber() == null) {
            return null;
        }
        return supportRequest.getPhoneCountryCode() + " " + supportRequest.getPhoneNumber();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
