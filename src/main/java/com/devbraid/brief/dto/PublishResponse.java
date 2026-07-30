package com.devbraid.brief.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishResponse {

    private Boolean success;
    private String publishUrl;
    private String message;
    private OffsetDateTime publishedAt;
}
