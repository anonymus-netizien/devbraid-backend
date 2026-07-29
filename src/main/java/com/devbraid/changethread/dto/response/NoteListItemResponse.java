package com.devbraid.changethread.dto.response;

import com.devbraid.changethread.entity.NoteContext;
import com.devbraid.changethread.entity.NoteStatus;
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
public class NoteListItemResponse {

    private UUID id;
    private UUID threadId;
    private String threadTitle;
    private String repositoryFullName;
    private String decision;
    private String rationale;
    private String alternatives;
    private String impact;
    private NoteContext context;
    private NoteStatus status;
    private OffsetDateTime createdAt;
}
