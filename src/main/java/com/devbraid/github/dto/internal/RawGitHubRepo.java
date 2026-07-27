package com.devbraid.github.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawGitHubRepo {
    @JsonProperty("full_name")
    private String fullName;
    @JsonProperty("default_branch")
    private String defaultBranch;
    @JsonProperty("private")
    private boolean isPrivate;
}
