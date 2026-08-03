package com.devbraid.changethread.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a manual snapshot of a thread at its current state.")
public class CreateSnapshotRequest {

    /**
     * Optional note to annotate the snapshot.
     */
    @Schema(description = "Optional annotation for the snapshot", example = "Captured before refactor", nullable = true)
    private String note;
}
