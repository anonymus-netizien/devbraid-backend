package com.devbraid.github.exception;

/**
 * Thrown when a GitHub PAT fails validation (expired/revoked).
 * Unlike {@link GitHubTokenInvalidException} which returns 401,
 * this returns 200 with valid:false on the /status endpoint
 * so the frontend can gracefully show "Token expired, reconnect".
 */
public class GitHubTokenExpiredException extends RuntimeException {
    public GitHubTokenExpiredException(String message) {
        super(message);
    }
}
