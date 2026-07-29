package com.devbraid.brief.dto;

import com.devbraid.changethread.entity.ThreadStatus;
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
public class BriefListItemResponse {

    private UUID id;
    private UUID threadId;
    private String threadTitle;
    private String repositoryFullName;
    private String headBranch;
    private String baseBranch;
    private ThreadStatus threadStatus;
    private Boolean publishedToGithub;
    private OffsetDateTime createdAt;
}
