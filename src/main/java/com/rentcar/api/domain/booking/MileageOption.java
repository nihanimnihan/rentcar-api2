package com.rentcar.api.domain.booking;

/**
 * Whether the customer chose the included km allowance or paid for unlimited km.
 * The backend uses this to apply the unlimited-km surcharge at booking time;
 * it is persisted as a snapshot so historical bookings are not affected by
 * future pricing-config changes.
 */
public enum MileageOption {
    INCLUDED,
    UNLIMITED
}
