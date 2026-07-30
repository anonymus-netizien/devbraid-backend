package com.devbraid.changethread.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateThreadEventRequest {

    @NotBlank(message = "Summary is required")
    @Size(max = 500, message = "Summary must be less than 500 characters")
    private String summary;

    private String metadata;
}
