package com.devbraid.github.exception;

/**
 * GitHub returned 403 for a write operation — typically the PAT lacks the
 * required scope (e.g. Issues/PR-comment write access).
 */
public class GitHubForbiddenException extends RuntimeException {
    public GitHubForbiddenException(String message) {
        super(message);
    }
}
