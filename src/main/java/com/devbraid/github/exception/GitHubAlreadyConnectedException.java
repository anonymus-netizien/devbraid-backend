package com.devbraid.github.exception;

public class GitHubAlreadyConnectedException extends RuntimeException {
    public GitHubAlreadyConnectedException(String message) {
        super(message);
    }
}
