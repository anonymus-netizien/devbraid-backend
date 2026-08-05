package com.devbraid.github.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCommentRequest {
    private String path;
    private int line;
    private String side;
    private String body;
}
