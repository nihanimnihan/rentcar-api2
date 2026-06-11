package com.rentcar.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthUserResponse {
    private String email;
    private String firstName;
    private String lastName;
    private String country;
    private boolean profileComplete;
    private String role;
}
