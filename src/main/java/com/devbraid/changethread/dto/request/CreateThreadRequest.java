package com.devbraid.changethread.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateThreadRequest {

    @NotBlank(message = "Repository full name is required")
    @Size(max = 500, message = "Repository full name must be less than 500 characters")
    private String repositoryFullName;

    @NotBlank(message = "Head branch is required")
    private String headBranch;

    private String baseBranch;

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must be less than 500 characters")
    private String title;

    private String description;
}
