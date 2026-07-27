package com.devbraid.github.exception;

public class GitHubRateLimitException extends RuntimeException {
    public GitHubRateLimitException(String message) {
        super(message);
    }
}
