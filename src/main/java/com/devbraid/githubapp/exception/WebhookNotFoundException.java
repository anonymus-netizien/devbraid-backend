package com.devbraid.githubapp.exception;

public class WebhookNotFoundException extends RuntimeException {

    public WebhookNotFoundException(String message) {
        super(message);
    }
}
