package com.pharmacy.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email không được để trống")
    public String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    public String password;
}