package com.devbraid.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of a successful login or token refresh. `accessToken` carries the bearer token; `refreshToken` is always null here because it is delivered as an httpOnly cookie.")
public class LoginResponse {
    @Schema(description = "JWT access token — send as `Authorization: Bearer <token>`", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;
    @Schema(description = "Always null in the body — the refresh token is set as an httpOnly cookie instead", nullable = true)
    private String refreshToken;
    @Schema(description = "Token issue time (UTC)", example = "2026-08-03T14:30:30Z")
    private Instant issuedAt;
    @Schema(description = "Access token expiry time (UTC)", example = "2026-08-03T15:00:30Z")
    private Instant expiresAt;
    @Schema(description = "User's full name", example = "Admin User")
    private String fullName;
    @Schema(description = "User's email", example = "admin@devbraid.com")
    private String email;
    @Schema(description = "User ID", example = "019fbdef-4a53-7ee6-ac4c-259509f6dbf0")
    private UUID userId;
    @Schema(description = "User role", example = "DEVELOPER")
    private String role;
}
