package com.devbraid.githubapp.exception;

public class WebhookPayloadInvalidException extends RuntimeException {

    public WebhookPayloadInvalidException(String message) {
        super(message);
    }

    public WebhookPayloadInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
