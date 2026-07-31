package com.devbraid.githubapp.exception;

public class WebhookSignatureInvalidException extends RuntimeException {

    public WebhookSignatureInvalidException(String message) {
        super(message);
    }
}
