package com.devbraid.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials for logging in. A successful login returns the access token in the body and sets the refresh token as an httpOnly cookie.")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Registered email address", example = "admin@devbraid.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password", example = "heyadmin@devbraid")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}
