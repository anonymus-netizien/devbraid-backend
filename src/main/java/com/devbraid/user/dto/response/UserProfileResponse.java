package com.devbraid.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "The authenticated user's profile.")
public class UserProfileResponse {
    @Schema(description = "User ID", example = "019fbdef-4a53-7ee6-ac4c-259509f6dbf0")
    private UUID id;
    @Schema(description = "Full name", example = "Admin User")
    private String fullName;
    @Schema(description = "Email address", example = "admin@devbraid.com")
    private String email;
    @Schema(description = "Role", example = "DEVELOPER")
    private String role;
    @Schema(description = "Account creation time (UTC)", example = "2026-08-03T14:00:00Z")
    private OffsetDateTime createdAt;
}
