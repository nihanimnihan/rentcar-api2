package com.rentcar.api.controller;

import com.rentcar.api.domain.support.SupportRequestStatus;
import com.rentcar.api.dto.admin.AdminSupportRequestDetail;
import com.rentcar.api.dto.admin.AdminSupportRequestPage;
import com.rentcar.api.service.SupportRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/support-requests")
@RequiredArgsConstructor
public class AdminSupportRequestController {

    private final SupportRequestService supportRequestService;

    @GetMapping
    public AdminSupportRequestPage listSupportRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) SupportRequestStatus status
    ) {
        log.info("Admin action=listSupportRequests page={} size={} status={}", page, size, status);
        return supportRequestService.listAdminRequests(page, size, status);
    }

    @GetMapping("/{id}")
    public AdminSupportRequestDetail getSupportRequest(@PathVariable Long id) {
        log.info("Admin action=getSupportRequest targetSupportRequestId={}", id);
        return supportRequestService.getAdminRequest(id);
    }

    @PostMapping("/{id}/resolve")
    public AdminSupportRequestDetail resolveSupportRequest(@PathVariable Long id) {
        log.info("Admin action=resolveSupportRequest targetSupportRequestId={}", id);
        return supportRequestService.resolveAdminRequest(id);
    }
}
