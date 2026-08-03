package com.devbraid.changethread.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update thread metadata. Omitted fields are left unchanged.")
public class UpdateThreadRequest {

    @Size(max = 500, message = "Title must be less than 500 characters")
    @Schema(description = "New thread title", example = "Add OAuth login to the API", nullable = true)
    private String title;

    @Schema(description = "New description", example = "Introduces OAuth2 client credentials flow.", nullable = true)
    private String description;
}
