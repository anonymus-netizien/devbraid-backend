package com.devbraid.changethread.dto.request;

import com.devbraid.changethread.entity.NoteContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateNoteRequest {

    @NotNull(message = "Context is required")
    private NoteContext context;

    private String contextRef;

    @NotBlank(message = "Decision is required")
    private String decision;

    @NotBlank(message = "Rationale is required")
    private String rationale;

    private String alternatives;

    private String impact;
}
