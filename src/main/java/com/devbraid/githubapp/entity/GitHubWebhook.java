package com.devbraid.githubapp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "github_webhooks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class GitHubWebhook {

    @Id
    @Generated(event = EventType.INSERT)
    private UUID id;

    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "delivery_id", length = 255)
    private String deliveryId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "processing_error")
    private String processingError;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    // Setters for mutable fields
    public void setProcessed(Boolean processed) {
        this.processed = processed;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
