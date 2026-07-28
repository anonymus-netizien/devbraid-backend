package com.devbraid.brief.dto;

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
public class BriefResponse {

    private UUID id;
    private UUID threadId;
    private String content;
    private Boolean publishedToGithub;
    private String publishUrl;
    private OffsetDateTime createdAt;
}
