package com.devbraid.changethread.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update a file comment. Omitted fields are left unchanged.")
public class UpdateFileCommentRequest {

    @Size(max = 10000, message = "Content must be less than 10000 characters")
    @Schema(description = "New comment body", example = "Updated: consider extracting this to a helper.", nullable = true)
    private String content;

    @Schema(description = "New start line", example = "37", nullable = true)
    private Integer lineStart;

    @Schema(description = "New end line", example = "42", nullable = true)
    private Integer lineEnd;

    @Schema(description = "New comment status", example = "RESOLVED", nullable = true)
    private String status;
}
