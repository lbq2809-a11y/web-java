package com.pharmacy.auth.dto.response;

public class AuthResponse {

    public String token;
    public String email;
    public String fullName;
    public String role;

    public AuthResponse(String token, String email, String fullName, String role) {
        this.token = token;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }
}