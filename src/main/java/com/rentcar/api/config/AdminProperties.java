package com.rentcar.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Admin credentials loaded from application properties.
 * Set via rentcar.admin.username and rentcar.admin.password.
 * In production these must be supplied as environment variables — never hardcode real values.
 */
@Validated
@ConfigurationProperties(prefix = "rentcar.admin")
public record AdminProperties(

        @NotBlank(message = "rentcar.admin.username must be set")
        String username,

        @NotBlank(message = "rentcar.admin.password must be set")
        String password
) {
}
