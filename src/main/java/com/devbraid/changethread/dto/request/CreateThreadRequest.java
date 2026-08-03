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
@Schema(description = "Create a Change Thread: a branch pair in a repository whose diff DevBraid captures and analyzes.")
public class CreateThreadRequest {

    @NotBlank(message = "Repository full name is required")
    @Size(max = 500, message = "Repository full name must be less than 500 characters")
    @Schema(description = "Repository in `owner/name` form", example = "anonymus-netizien/devbraid-backend")
    private String repositoryFullName;

    @NotBlank(message = "Head branch is required")
    @Schema(description = "Branch containing the change (the PR head)", example = "feature/oauth-login")
    private String headBranch;

    @Schema(description = "Base branch the change is compared against. Defaults to `develop` when omitted.",
            example = "develop", nullable = true)
    private String baseBranch;

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must be less than 500 characters")
    @Schema(description = "Thread title", example = "Add OAuth login to the API")
    private String title;

    @Schema(description = "Free-form description of the change", example = "Introduces OAuth2 client credentials flow.", nullable = true)
    private String description;
}
