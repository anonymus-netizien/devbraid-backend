package com.devbraid.githubapp.exception;

/**
 * Thrown when a GitHub OAuth call (token exchange or user fetch) fails.
 */
public class GitHubOAuthException extends RuntimeException {

    public GitHubOAuthException(String message) {
        super(message);
    }

    public GitHubOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
