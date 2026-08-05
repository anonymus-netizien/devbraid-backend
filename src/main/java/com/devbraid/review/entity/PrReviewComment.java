package com.devbraid.review.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Individual finding within an automated PR review, tied to a file and line.
 */
@Entity
@Table(name = "pr_review_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class PrReviewComment {

    @Id
    @Generated(event = EventType.INSERT)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private PrReview review;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingCategory category;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "github_comment_id")
    private Long githubCommentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Setters for update operations
    public void setReview(PrReview review) {
        this.review = review;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public void setSeverity(FindingSeverity severity) {
        this.severity = severity;
    }

    public void setCategory(FindingCategory category) {
        this.category = category;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setGithubCommentId(Long githubCommentId) {
        this.githubCommentId = githubCommentId;
    }
}
