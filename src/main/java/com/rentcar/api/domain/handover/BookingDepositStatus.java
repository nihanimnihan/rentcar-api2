package com.rentcar.api.domain.handover;

public enum BookingDepositStatus {
    NOT_COLLECTED,
    PAYMENT_LINK_CREATED,
    COLLECTED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    RETAINED,
    FAILED,
    EXPIRED
}
