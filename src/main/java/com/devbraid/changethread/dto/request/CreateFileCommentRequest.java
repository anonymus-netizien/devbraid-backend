package com.devbraid.changethread.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFileCommentRequest {

    @NotBlank(message = "File path is required")
    @Size(max = 1000, message = "File path must be less than 1000 characters")
    private String filePath;

    private Integer lineStart;

    private Integer lineEnd;

    @NotBlank(message = "Content is required")
    @Size(max = 10000, message = "Content must be less than 10000 characters")
    private String content;
}
