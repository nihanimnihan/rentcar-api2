package com.rentcar.api.controller;

import com.rentcar.api.dto.payment.PaymentResponse;
import com.rentcar.api.mapper.PaymentMapper;
import com.rentcar.api.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @GetMapping
    public List<PaymentResponse> getPayments() {
        return  paymentService.getPayments().stream().map(paymentMapper::toResponse).toList();
    }
}
