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
public class RawGitHubPullRequest {
    private int number;
    private String title;
    private String body;
    private String state;
    @JsonProperty("html_url")
    private String htmlUrl;
    private Head head;
    private Base base;
    private RawGitHubUser user;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Head {
        private String ref;
        private String sha;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Base {
        private String ref;
        private String sha;
    }
}
