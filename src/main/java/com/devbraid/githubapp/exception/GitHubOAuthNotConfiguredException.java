package com.devbraid.githubapp.exception;

/**
 * Thrown when GitHub OAuth identity linking is attempted without
 * github.oauth.client-id / client-secret configured.
 */
public class GitHubOAuthNotConfiguredException extends RuntimeException {

    public GitHubOAuthNotConfiguredException(String message) {
        super(message);
    }
}
