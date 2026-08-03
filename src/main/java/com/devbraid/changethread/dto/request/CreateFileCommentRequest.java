package com.devbraid.changethread.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a comment anchored to a file path in a change thread.")
public class CreateFileCommentRequest {

    @NotBlank(message = "File path is required")
    @Size(max = 1000, message = "File path must be less than 1000 characters")
    @Schema(description = "File path the comment anchors to", example = "src/main/java/com/devbraid/security/JwtTokenProvider.java")
    private String filePath;

    @Schema(description = "Start line (1-based) of the anchored range", example = "37", nullable = true)
    private Integer lineStart;

    @Schema(description = "End line (inclusive) of the anchored range", example = "42", nullable = true)
    private Integer lineEnd;

    @NotBlank(message = "Content is required")
    @Size(max = 10000, message = "Content must be less than 10000 characters")
    @Schema(description = "Comment body, max 10 000 characters", example = "Consider extracting this to a helper method.")
    private String content;
}
