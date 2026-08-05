package com.devbraid.githubapp;

/**
 * Thrown when GitHub App installation token exchange fails.
 */
public class GitHubAppTokenException extends RuntimeException {

    public GitHubAppTokenException(String message) {
        super(message);
    }
}
