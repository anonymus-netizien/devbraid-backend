package com.devbraid.user.dto.request;

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
public class OtpVerifyRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    private String otp;

    // Explicit getters for Java 25 Lombok compatibility
    public String getEmail() {
        return email;
    }

    public String getOtp() {
        return otp;
    }
}
