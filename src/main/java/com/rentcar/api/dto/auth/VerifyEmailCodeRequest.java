package com.rentcar.api.dto.auth;

public record VerifyEmailCodeRequest(String email, String code) {
}
