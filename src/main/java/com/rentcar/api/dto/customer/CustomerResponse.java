package com.rentcar.api.dto.customer;

public record CustomerResponse(
        Long id,
        String fullName,
        String email,
        String phone
) {
}
