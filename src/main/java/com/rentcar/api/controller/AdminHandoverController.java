package com.rentcar.api.controller;

import com.rentcar.api.dto.admin.handover.BookingDepositResponse;
import com.rentcar.api.dto.admin.handover.DepositPaymentIntentResponse;
import com.rentcar.api.dto.admin.handover.DepositRefundRequest;
import com.rentcar.api.dto.admin.handover.HandoverPageResponse;
import com.rentcar.api.dto.admin.handover.ManualDepositCollectionRequest;
import com.rentcar.api.dto.admin.handover.SaveHandoverRequest;
import com.rentcar.api.dto.admin.handover.VehicleHandoverResponse;
import com.rentcar.api.service.AdminHandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bookings/{bookingId}")
@RequiredArgsConstructor
public class AdminHandoverController {

    private final AdminHandoverService adminHandoverService;

    @GetMapping("/handover")
    public HandoverPageResponse getHandover(@PathVariable Long bookingId) {
        return adminHandoverService.getHandoverPage(bookingId);
    }

    @PostMapping("/deposit")
    public BookingDepositResponse ensureDeposit(@PathVariable Long bookingId) {
        return adminHandoverService.ensureDeposit(bookingId);
    }

    @PostMapping("/deposit/stripe-intent")
    public DepositPaymentIntentResponse createStripeDepositIntent(@PathVariable Long bookingId) {
        return adminHandoverService.createStripeDepositIntent(bookingId);
    }

    @PostMapping("/deposit/manual-collection")
    public BookingDepositResponse markManualDepositCollected(
            @PathVariable Long bookingId,
            @Valid @RequestBody ManualDepositCollectionRequest request) {
        return adminHandoverService.markManualDepositCollected(bookingId, request);
    }

    @PostMapping("/deposit/refund")
    public BookingDepositResponse refundDeposit(
            @PathVariable Long bookingId,
            @Valid @RequestBody DepositRefundRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = auth != null && auth.getName() != null ? auth.getName() : "admin";
        return adminHandoverService.refundDeposit(bookingId, request, actor);
    }

    @PostMapping("/handover")
    public VehicleHandoverResponse saveHandover(
            @PathVariable Long bookingId,
            @Valid @RequestBody SaveHandoverRequest request) {
        return adminHandoverService.saveHandover(bookingId, request);
    }

    @PostMapping("/picked-up")
    public HandoverPageResponse markPickedUp(@PathVariable Long bookingId) {
        return adminHandoverService.markPickedUp(bookingId);
    }
}
