package com.rentcar.api.config;


/**
 * Admin credentials loaded from application properties.
 * Set via rentcar.admin.username and rentcar.admin.password.
 * In production these must be supplied as environment variables — never hardcode real values.
 */
// AdminProperties was used for Basic Auth admin credentials. No longer used — retained as a placeholder.
public record AdminProperties(String username, String password) {
}
