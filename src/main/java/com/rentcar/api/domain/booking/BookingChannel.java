package com.rentcar.api.domain.booking;

/**
 * Channel through which a booking lifecycle action was performed.
 *
 * WEB          – customer-facing website (standard checkout, manage-booking)
 * ADMIN_PANEL  – internal back-office UI
 * API          – direct REST API call (partner integration, headless)
 * SYSTEM       – automated process
 */
public enum BookingChannel {
    WEB,
    ADMIN_PANEL,
    API,
    SYSTEM
}
