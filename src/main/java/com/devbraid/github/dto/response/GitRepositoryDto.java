package com.devbraid.github.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitRepositoryDto {
    private String fullName;
    private String defaultBranch;
    @JsonProperty("isPrivate")
    private boolean isPrivate;
}
