package com.rentcar.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${stripe.publishable-key:}")
    private String stripePublishableKey;

    @GetMapping("/stripe-publishable-key")
    public ResponseEntity<Map<String, String>> getStripePublishableKey() {
        return ResponseEntity.ok(Map.of("publishableKey", stripePublishableKey));
    }

}
