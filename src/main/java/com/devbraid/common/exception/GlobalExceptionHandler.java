package com.devbraid.common.exception;

import com.devbraid.changethread.exception.BriefNotFoundException;
import com.devbraid.changethread.exception.NoteNotFoundException;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.common.ApiResponse;
import com.devbraid.github.exception.*;
import com.devbraid.user.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ApiResponse<?>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }

    // ── User exceptions ──

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<?>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("GlobalExceptionHandler :: User already exists: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleUserNotFound(UserNotFoundException ex) {
        log.warn("GlobalExceptionHandler :: User not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("GlobalExceptionHandler :: Invalid credentials: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(RefreshTokenRevokedException.class)
    public ResponseEntity<ApiResponse<?>> handleRefreshTokenRevoked(RefreshTokenRevokedException ex) {
        log.warn("GlobalExceptionHandler :: Refresh token revoked: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // ── OTP exceptions ──

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ApiResponse<?>> handleOtpExpired(OtpExpiredException ex) {
        log.warn("GlobalExceptionHandler :: OTP expired: {}", ex.getMessage());
        return error(HttpStatus.GONE, ex.getMessage());
    }

    @ExceptionHandler(OtpInvalidException.class)
    public ResponseEntity<ApiResponse<?>> handleOtpInvalid(OtpInvalidException ex) {
        log.warn("GlobalExceptionHandler :: OTP invalid: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public ResponseEntity<ApiResponse<?>> handleOtpRateLimit(OtpRateLimitException ex) {
        log.warn("GlobalExceptionHandler :: OTP rate limited: {}", ex.getMessage());
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleRateLimit(RateLimitExceededException ex) {
        log.warn("GlobalExceptionHandler :: Rate limited: {}", ex.getMessage());
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // ── GitHub exceptions ──

    @ExceptionHandler(GitHubAlreadyConnectedException.class)
    public ResponseEntity<ApiResponse<?>> handleGitHubAlreadyConnected(GitHubAlreadyConnectedException ex) {
        log.warn("GlobalExceptionHandler :: GitHub already connected: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(GitHubNotConnectedException.class)
    public ResponseEntity<ApiResponse<?>> handleGitHubNotConnected(GitHubNotConnectedException ex) {
        log.warn("GlobalExceptionHandler :: GitHub not connected: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(GitHubTokenInvalidException.class)
    public ResponseEntity<ApiResponse<?>> handleGitHubTokenInvalid(GitHubTokenInvalidException ex) {
        log.warn("GlobalExceptionHandler :: GitHub token invalid: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(GitHubTokenExpiredException.class)
    public ResponseEntity<ApiResponse<?>> handleGitHubTokenExpired(GitHubTokenExpiredException ex) {
        log.warn("GlobalExceptionHandler :: GitHub token expired/invalid: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(GitHubNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleGitHubNotFound(GitHubNotFoundException ex) {
        log.warn("GlobalExceptionHandler :: GitHub resource not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(GitHubForbiddenException.class)
    public ResponseEntity<ApiResponse<?>> handleGitHubForbidden(GitHubForbiddenException ex) {
        log.warn("GlobalExceptionHandler :: GitHub forbidden: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(GitHubRateLimitException.class)
    public ResponseEntity<ApiResponse<?>> handleGitHubRateLimit(GitHubRateLimitException ex) {
        log.warn("GlobalExceptionHandler :: GitHub rate limited: {}", ex.getMessage());
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // ── Change Thread exceptions ──

    @ExceptionHandler(ThreadNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleThreadNotFound(ThreadNotFoundException ex) {
        log.warn("GlobalExceptionHandler :: Thread not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoteNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoteNotFound(NoteNotFoundException ex) {
        log.warn("GlobalExceptionHandler :: Note not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BriefNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleBriefNotFound(BriefNotFoundException ex) {
        log.warn("GlobalExceptionHandler :: Brief not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Kept for Spring Security method-security (@PreAuthorize/@Secured) and future RBAC.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("GlobalExceptionHandler :: Access denied: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // ── IO exceptions (e.g., request body read failures) ──

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<?>> handleIOException(IOException ex) {
        log.warn("GlobalExceptionHandler :: IO error reading request: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Failed to read request body: " + ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("GlobalExceptionHandler :: Data integrity violation: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, "Conflict: duplicate request or invalid reference");
    }

    // ── Spring resource exceptions ──

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("GlobalExceptionHandler :: Resource not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, "Resource not found: " + ex.getResourcePath());
    }

    // ── Generic exceptions ──

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("GlobalExceptionHandler :: Type mismatch for parameter {}: {}", ex.getName(), ex.getMessage());
        String msg = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("GlobalExceptionHandler :: Missing required parameter: {}", ex.getParameterName());
        return error(HttpStatus.BAD_REQUEST,
                "Required request parameter '" + ex.getParameterName() + "' is not present");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("GlobalExceptionHandler :: Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleMalformedRequest(HttpMessageNotReadableException ex) {
        log.warn("GlobalExceptionHandler :: Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        String msg = ex.getMostSpecificCause() instanceof IllegalArgumentException
                ? ex.getMostSpecificCause().getMessage()
                : "Invalid request body: " + ex.getMostSpecificCause().getMessage();
        return ResponseEntity.badRequest().body(ApiResponse.error(msg));
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
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Runtime error occurred");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex) {
        log.error(
                "GlobalExceptionHandler :: Unexpected error: {}",
                ex.getMessage(),
                ex
        );
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
}
