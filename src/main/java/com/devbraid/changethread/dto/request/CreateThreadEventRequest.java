package com.devbraid.changethread.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a manual event on a thread's timeline.")
public class CreateThreadEventRequest {

    @NotBlank(message = "Summary is required")
    @Size(max = 500, message = "Summary must be less than 500 characters")
    @Schema(description = "Short event summary", example = "PR merged to develop")
    private String summary;

    @Schema(description = "Optional JSON metadata attached to the event", example = "{\"prNumber\": 42}", nullable = true)
    private String metadata;
}
