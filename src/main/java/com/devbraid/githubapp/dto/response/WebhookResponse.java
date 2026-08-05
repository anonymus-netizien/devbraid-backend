package com.devbraid.githubapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookResponse {

    private UUID id;
    private Long installationId;
    private String deliveryId;
    private String eventType;
    private String action;
    private Boolean processed;
    private String processingError;
    private OffsetDateTime receivedAt;
    private OffsetDateTime processedAt;
    private Boolean replayed;
}
