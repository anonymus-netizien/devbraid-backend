package com.devbraid.changethread.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFileCommentRequest {

    @Size(max = 10000, message = "Content must be less than 10000 characters")
    private String content;

    private Integer lineStart;

    private Integer lineEnd;

    private String status;
}
