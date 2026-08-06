package com.devbraid.changethread.dto.request;

import com.devbraid.changethread.entity.NoteContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a decision note on a thread. `contextRef` is required when `context` is `COMMIT` (commit sha) or `FILE` (file path).")
public class CreateNoteRequest {

    @NotNull(message = "Context is required")
    @Schema(description = "What the note is anchored to: `COMMIT`, `FILE` or `THREAD`", example = "COMMIT")
    private NoteContext context;

    @Schema(description = "Anchor reference — commit sha for `COMMIT`, file path for `FILE`. Ignored for `THREAD`.",
            example = "a1b2c3d4e5f6...", nullable = true)
    private String contextRef;

    @NotBlank(message = "Decision is required")
    @Schema(description = "The decision that was made", example = "Use HMAC-SHA256 over the raw body")
    private String decision;

    @NotBlank(message = "Rationale is required")
    @Schema(description = "Why the decision was made", example = "GitHub signs webhook payloads this way and it avoids re-parsing the JSON")
    private String rationale;

    @Schema(description = "Alternatives considered (optional)", example = "HMAC-SHA1; plain secret comparison", nullable = true)
    private String alternatives;

    @Schema(description = "Impact of the decision (optional)", example = "Webhook events now fail closed on bad signatures", nullable = true)
    private String impact;
}
