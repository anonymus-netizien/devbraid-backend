package com.devbraid.changethread.dto.response;

import com.devbraid.changethread.entity.CommentStatus;
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
public class FileCommentResponse {

    private UUID id;
    private UUID threadId;
    private UUID authorId;
    private String filePath;
    private Integer lineStart;
    private Integer lineEnd;
    private String content;
    private CommentStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
