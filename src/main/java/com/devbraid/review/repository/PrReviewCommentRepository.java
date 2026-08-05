package com.devbraid.review.repository;

import com.devbraid.review.entity.PrReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrReviewCommentRepository extends JpaRepository<PrReviewComment, UUID> {

    List<PrReviewComment> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);
}
