package com.devbraid.review.repository;

import com.devbraid.review.entity.PrReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrReviewRepository extends JpaRepository<PrReview, UUID> {

    /**
     * Idempotency guard — one review per thread per head SHA.
     */
    Optional<PrReview> findByThreadIdAndHeadSha(UUID threadId, String headSha);

    List<PrReview> findByThreadIdOrderByCreatedAtDesc(UUID threadId);

    Optional<PrReview> findByIdAndThreadUserId(UUID id, UUID userId);

    /**
     * Paginated reviews owned by a user across all their threads.
     * The thread owns the user_id, so ownership is resolved through thread.user.
     */
    @Query(value = "SELECT r FROM PrReview r JOIN FETCH r.thread t WHERE t.user.id = :userId ORDER BY r.createdAt DESC",
            countQuery = "SELECT count(r) FROM PrReview r WHERE r.thread.user.id = :userId")
    Page<PrReview> findAllByThreadUserId(@Param("userId") UUID userId, Pageable pageable);
}
