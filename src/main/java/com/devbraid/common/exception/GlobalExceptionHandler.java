package com.devbraid.common.exception;

import com.devbraid.common.ApiResponse;
import com.devbraid.user.exception.InvalidCredentialsException;
import com.devbraid.user.exception.RefreshTokenRevokedException;
import com.devbraid.user.exception.UserAlreadyExistsException;
import com.devbraid.user.exception.UserNotFoundException;
import com.devbraid.user.exception.OtpExpiredException;
import com.devbraid.user.exception.OtpInvalidException;
import com.devbraid.user.exception.OtpRateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<?>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("GlobalExceptionHandler :: User already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiResponse.error(ex.getMessage())
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleUserNotFound(UserNotFoundException ex) {
        log.warn("GlobalExceptionHandler :: User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.error(ex.getMessage())
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("GlobalExceptionHandler :: Invalid credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.error(ex.getMessage())
        );
    }

    @ExceptionHandler(RefreshTokenRevokedException.class)
    public ResponseEntity<ApiResponse<?>> handleRefreshTokenRevoked(RefreshTokenRevokedException ex) {
        log.warn("GlobalExceptionHandler :: Refresh token revoked: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.error(ex.getMessage())
        );
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ApiResponse<?>> handleOtpExpired(OtpExpiredException ex) {
        log.warn("GlobalExceptionHandler :: OTP expired: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE).body(
                ApiResponse.error(ex.getMessage())
        );
    }

    @ExceptionHandler(OtpInvalidException.class)
    public ResponseEntity<ApiResponse<?>> handleOtpInvalid(OtpInvalidException ex) {
        log.warn("GlobalExceptionHandler :: OTP invalid: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.error(ex.getMessage())
        );
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public ResponseEntity<ApiResponse<?>> handleOtpRateLimit(OtpRateLimitException ex) {
        log.warn("GlobalExceptionHandler :: OTP rate limited: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                ApiResponse.error(ex.getMessage())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("GlobalExceptionHandler :: Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiResponse.error(ex.getMessage())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (a, b) -> a
                ));
        log.warn("GlobalExceptionHandler :: Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(
                ApiResponse.error("Validation failed", errors)
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntimeException(RuntimeException ex) {
        log.error("GlobalExceptionHandler :: Runtime exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.error("Runtime error occurred")
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
                ApiResponse.error("Internal server error")
        );
    }
}
