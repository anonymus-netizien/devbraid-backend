package com.devbraid.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ponytail: Lombok with @Data should generate getters, but Java 25
 * has compatibility issues with this Lombok version.
 * Explicit getters are defensive — remove when Lombok updates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to verify a one-time password. When the email has a pending registration, verification finalizes it.")
public class OtpVerifyRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Email address the OTP was sent to", example = "admin@devbraid.com")
    private String email;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    @Schema(description = "6-digit one-time password", example = "123456")
    private String otp;

    // Explicit getters for Java 25 Lombok compatibility
    public String getEmail() {
        return email;
    }

    public String getOtp() {
        return otp;
    }
}
