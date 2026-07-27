package com.devbraid.github.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectRequest {
    @NotBlank(message = "Personal access token is required")
    private String personalAccessToken;
}
