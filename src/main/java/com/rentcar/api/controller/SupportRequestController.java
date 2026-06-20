package com.rentcar.api.controller;

import com.rentcar.api.dto.support.CreateSupportRequestRequest;
import com.rentcar.api.dto.support.SupportRequestResponse;
import com.rentcar.api.service.SupportRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support-requests")
@RequiredArgsConstructor
public class SupportRequestController {

    private final SupportRequestService supportRequestService;

    @PostMapping
    public ResponseEntity<SupportRequestResponse> create(@Valid @RequestBody CreateSupportRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supportRequestService.create(request));
    }
}
