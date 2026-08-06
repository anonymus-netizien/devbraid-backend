package com.devbraid.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to send a one-time password to an email address.")
public class OtpSendRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Email address to send the OTP to", example = "admin@devbraid.com")
    private String email;
}
