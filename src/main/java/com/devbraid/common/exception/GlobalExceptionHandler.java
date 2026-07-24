package com.devbraid.common.exception;

import com.devbraid.common.ApiResponse;
import com.devbraid.user.exception.InvalidCredentialsException;
import com.devbraid.user.exception.UserAlreadyExistsException;
import com.devbraid.user.exception.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<?>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("GlobalExceptionHandler :: User already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiResponse.error(ex.getMessage(), HttpStatus.CONFLICT.value())
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleUserNotFound(UserNotFoundException ex) {
        log.warn("GlobalExceptionHandler :: User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.error(ex.getMessage(), HttpStatus.NOT_FOUND.value())
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("GlobalExceptionHandler :: Invalid credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.error(ex.getMessage(), HttpStatus.UNAUTHORIZED.value())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("GlobalExceptionHandler :: Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiResponse.error(
                        ex.getMessage(),
                        HttpStatus.BAD_REQUEST.value()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errors = ex
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("GlobalExceptionHandler :: Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(
                ApiResponse.error(errors, HttpStatus.BAD_REQUEST.value())
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntimeException(RuntimeException ex) {
        log.error("GlobalExceptionHandler :: Runtime exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.error(
                        "Runtime error occurred",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex) {
        log.error(
                "GlobalExceptionHandler :: Unexpected error: {}",
                ex.getMessage(),
                ex
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.error(
                        "Internal server error",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                )
        );
    }
}
