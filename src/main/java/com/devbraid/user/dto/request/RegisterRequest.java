package com.devbraid.user.dto.request;

import com.devbraid.common.util.DisposableEmailValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Registration request. The account is finalized only after the email is verified via `POST /auth/otp/verify`. Disposable email domains are rejected with 400.")
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 80, message = "Full name must be at most 80 characters")
    @Schema(description = "User's full name", example = "Jane Doe")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Email address (must not be a disposable domain)", example = "jane@devbraid.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Size(max = 72, message = "Password must be at most 72 characters")
    @Schema(description = "Password, 8–72 characters", example = "sup3r-secret-pass")
    private String password;

    public RegisterRequest() {
    }

    public RegisterRequest(String fullName, String email, String password) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public boolean isDisposableEmail() {
        return DisposableEmailValidator.isDisposable(email);
    }
}
