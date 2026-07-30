package com.devbraid.changethread.dto.response;

import com.devbraid.changethread.entity.ThreadEventType;
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
public class ThreadEventResponse {

    private UUID id;
    private UUID threadId;
    private UUID actorId;
    private ThreadEventType type;
    private String summary;
    private String metadata;
    private OffsetDateTime createdAt;
}
