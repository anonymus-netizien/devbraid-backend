package com.devbraid.githubapp.exception;

public class WebhookPayloadTooLargeException extends RuntimeException {

    public WebhookPayloadTooLargeException(String message) {
        super(message);
    }
}
