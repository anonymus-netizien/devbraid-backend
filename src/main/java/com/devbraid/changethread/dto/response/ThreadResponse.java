package com.devbraid.changethread.dto.response;

import com.devbraid.changethread.entity.RiskLevel;
import com.devbraid.changethread.entity.ThreadSource;
import com.devbraid.changethread.entity.ThreadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreadResponse {

    private UUID id;
    private String repositoryFullName;
    private String headBranch;
    private String baseBranch;
    private String title;
    private String description;
    private ThreadSource source;
    private ThreadStatus status;
    private String commitSha;
    private String commits;
    private String changedFiles;
    private RiskLevel riskLevel;
    private String riskReport;
    private List<NoteResponse> notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
