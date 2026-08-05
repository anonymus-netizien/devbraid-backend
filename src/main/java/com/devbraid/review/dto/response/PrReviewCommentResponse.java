package com.devbraid.review.dto.response;

import com.devbraid.review.entity.FindingCategory;
import com.devbraid.review.entity.FindingSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrReviewCommentResponse {

    private UUID id;
    private String filePath;
    private Integer lineNumber;
    private FindingSeverity severity;
    private FindingCategory category;
    private String title;
    private String body;
    private Long githubCommentId;
}
