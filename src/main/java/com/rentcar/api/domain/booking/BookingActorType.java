package com.rentcar.api.domain.booking;

/**
 * Who/what initiated a booking lifecycle action.
 *
 * CUSTOMER_ANONYMOUS    – unauthenticated customer (manage-booking flow, standard checkout)
 * CUSTOMER_AUTHENTICATED – future: logged-in customer account
 * ADMIN                  – back-office staff via admin panel
 * SYSTEM                 – automated process (e.g. scheduled job, payment webhook)
 */
public enum BookingActorType {
    CUSTOMER_ANONYMOUS,
    CUSTOMER_AUTHENTICATED,
    ADMIN,
    SYSTEM
}
