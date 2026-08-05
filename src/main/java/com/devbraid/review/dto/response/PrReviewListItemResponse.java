package com.devbraid.review.dto.response;

import com.devbraid.review.entity.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrReviewListItemResponse {

    private UUID id;
    private UUID threadId;
    private int prNumber;
    private String headSha;
    private ReviewStatus status;
    private boolean published;
    private OffsetDateTime createdAt;
}
