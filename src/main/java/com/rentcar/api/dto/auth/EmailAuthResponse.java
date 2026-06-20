package com.rentcar.api.dto.auth;

public record EmailAuthResponse(String status, String profileToken) {
    public static EmailAuthResponse codeSent() {
        return new EmailAuthResponse("CODE_SENT", null);
    }

    public static EmailAuthResponse loggedIn() {
        return new EmailAuthResponse("LOGGED_IN", null);
    }

    public static EmailAuthResponse profileRequired(String profileToken) {
        return new EmailAuthResponse("PROFILE_REQUIRED", profileToken);
    }
}
