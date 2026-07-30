package com.devbraid.github.exception;

public class GitHubNotConnectedException extends RuntimeException {
    public GitHubNotConnectedException(String message) {
        super(message);
    }
}
