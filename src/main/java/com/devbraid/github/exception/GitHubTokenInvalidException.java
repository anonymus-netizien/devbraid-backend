package com.devbraid.github.exception;

public class GitHubTokenInvalidException extends RuntimeException {
    public GitHubTokenInvalidException(String message) {
        super(message);
    }
}
