package com.devbraid.github.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Connect the user's GitHub account with a personal access token. The token is stored encrypted.")
public class ConnectRequest {
    @NotBlank(message = "Personal access token is required")
    @Schema(description = "GitHub personal access token (repo + read:user scopes recommended)", example = "ghp_xxxxxxxxxxxxxxxxxxxx", format = "password")
    private String personalAccessToken;
}
