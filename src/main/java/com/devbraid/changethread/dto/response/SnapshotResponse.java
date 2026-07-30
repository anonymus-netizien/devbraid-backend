package com.devbraid.changethread.dto.response;

import com.devbraid.changethread.entity.SnapshotType;
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
public class SnapshotResponse {

    private UUID id;
    private UUID threadId;
    private UUID userId;
    private String repositoryFullName;
    private String headBranch;
    private String baseBranch;
    private String commitSha;
    private String commits;
    private String changedFiles;
    private String title;
    private String description;
    private SnapshotType type;
    private String note;
    private OffsetDateTime createdAt;
}
