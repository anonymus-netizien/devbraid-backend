package com.devbraid.github.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCompareResponse {
    private String status;
    @JsonProperty("ahead_by")
    private int aheadBy;
    @JsonProperty("behind_by")
    private int behindBy;
    @JsonProperty("total_commits")
    private int totalCommits;
    @JsonProperty("commits")
    private List<CommitSummaryDto> commits;
    @JsonProperty("files")
    private List<ChangedFileDto> files;
}
