package com.rentcar.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${stripe.publishable-key:}")
    private String stripePublishableKey;

    @GetMapping("/stripe-publishable-key")
    public ResponseEntity<Map<String, String>> getStripePublishableKey() {
        return ResponseEntity.ok(Map.of("publishableKey", stripePublishableKey));
    }

    private final Environment environment;

    public ConfigController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/debug")
    public Map<String, Object> debug() {
        return Map.of(
                "activeProfiles", environment.getActiveProfiles(),
                "publishableKey", Objects.requireNonNull(environment.getProperty("stripe.publishable-key")),
                "apiKeyPresent", environment.getProperty("stripe.api-key") != null
        );
    }
}
