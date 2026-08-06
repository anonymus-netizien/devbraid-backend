package com.devbraid.changethread.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update a decision note. Omitted fields are left unchanged.")
public class UpdateNoteRequest {

    @Schema(description = "The decision that was made", example = "Use HMAC-SHA256 over the raw body", nullable = true)
    private String decision;

    @Schema(description = "Why the decision was made", example = "GitHub signs webhook payloads this way", nullable = true)
    private String rationale;

    @Schema(description = "Alternatives considered", example = "HMAC-SHA1; plain secret comparison", nullable = true)
    private String alternatives;

    @Schema(description = "Impact of the decision", example = "Webhook events now fail closed on bad signatures", nullable = true)
    private String impact;
}
