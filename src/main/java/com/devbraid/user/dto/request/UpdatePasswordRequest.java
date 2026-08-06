package com.devbraid.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Password change request. Requires the current password; fails with 400 when it is incorrect.")
public class UpdatePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Schema(description = "Current password", example = "old-secret-pass")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters")
    @Schema(description = "New password, 8–128 characters", example = "new-secret-pass")
    private String newPassword;
}
