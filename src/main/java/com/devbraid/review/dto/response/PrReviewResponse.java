package com.devbraid.review.dto.response;

import com.devbraid.review.entity.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrReviewResponse {

    private UUID id;
    private UUID threadId;
    private int prNumber;
    private String headSha;
    private ReviewStatus status;
    private String summary;
    private Map<String, Long> severityCounts;
    private boolean published;
    private String githubReviewUrl;
    private String error;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private List<PrReviewCommentResponse> comments;
}
