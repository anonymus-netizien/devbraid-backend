package com.devbraid.review.entity;

import com.devbraid.changethread.entity.ChangeThread;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Automated PR review for a ChangeThread (Code-Rabbit-style).
 * One review per thread/head SHA, with findings published back to GitHub.
 */
@Entity
@Table(name = "pr_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class PrReview {

    @Id
    @Generated(event = EventType.INSERT)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private ChangeThread thread;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "head_sha", nullable = false, length = 40)
    private String headSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.RUNNING;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "severity_counts", columnDefinition = "jsonb")
    private Map<String, Long> severityCounts;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "github_review_id")
    private Long githubReviewId;

    @Column(name = "github_review_url", length = 500)
    private String githubReviewUrl;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PrReviewComment> comments = new ArrayList<>();

    public void addComment(PrReviewComment comment) {
        this.comments.add(comment);
        comment.setReview(this);
    }

    // Setters for update operations
    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setSeverityCounts(Map<String, Long> severityCounts) {
        this.severityCounts = severityCounts;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public void setGithubReviewId(Long githubReviewId) {
        this.githubReviewId = githubReviewId;
    }

    public void setGithubReviewUrl(String githubReviewUrl) {
        this.githubReviewUrl = githubReviewUrl;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
