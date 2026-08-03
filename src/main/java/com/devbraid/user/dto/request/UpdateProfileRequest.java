package com.devbraid.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Profile update. Fields that are null or blank are left unchanged.")
public class UpdateProfileRequest {

    @Size(max = 100, message = "Full name must be less than 100 characters")
    @Schema(description = "New full name (optional — omitted fields are unchanged)", example = "Jane Q. Doe", nullable = true)
    private String fullName;
}
