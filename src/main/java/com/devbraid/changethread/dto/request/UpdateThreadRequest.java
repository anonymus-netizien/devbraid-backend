package com.devbraid.changethread.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateThreadRequest {

    @Size(max = 500, message = "Title must be less than 500 characters")
    private String title;

    private String description;
}
