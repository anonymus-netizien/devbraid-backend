package com.devbraid.user.exception;

public class OtpInvalidException extends RuntimeException {
    public OtpInvalidException(String message) {
        super(message);
    }
}
