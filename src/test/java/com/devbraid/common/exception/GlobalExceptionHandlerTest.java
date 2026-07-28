package com.devbraid.common.exception;

import com.devbraid.common.ApiResponse;
import com.devbraid.github.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── GitHub exception tests ──

    @Test
    @DisplayName("GitHubAlreadyConnectedException returns 409 CONFLICT")
    void handleGitHubAlreadyConnected_Returns409() {
        ResponseEntity<ApiResponse<?>> result =
                handler.handleGitHubAlreadyConnected(new GitHubAlreadyConnectedException("Already connected"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).contains("Already connected");
    }

    @Test
    @DisplayName("GitHubNotConnectedException returns 404 NOT_FOUND")
    void handleGitHubNotConnected_Returns404() {
        ResponseEntity<ApiResponse<?>> result =
                handler.handleGitHubNotConnected(new GitHubNotConnectedException("Not connected"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).contains("Not connected");
    }

    @Test
    @DisplayName("GitHubTokenInvalidException returns 401 UNAUTHORIZED")
    void handleGitHubTokenInvalid_Returns401() {
        ResponseEntity<ApiResponse<?>> result =
                handler.handleGitHubTokenInvalid(new GitHubTokenInvalidException("Token invalid"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).contains("Token invalid");
    }

    @Test
    @DisplayName("GitHubNotFoundException returns 404 NOT_FOUND")
    void handleGitHubNotFound_Returns404() {
        ResponseEntity<ApiResponse<?>> result =
                handler.handleGitHubNotFound(new GitHubNotFoundException("Resource not found"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).contains("Resource not found");
    }

    @Test
    @DisplayName("GitHubRateLimitException returns 429 TOO_MANY_REQUESTS")
    void handleGitHubRateLimit_Returns429() {
        ResponseEntity<ApiResponse<?>> result =
                handler.handleGitHubRateLimit(new GitHubRateLimitException("Rate limited"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).contains("Rate limited");
    }

    // ── Validation exception tests ──

    @Test
    @DisplayName("MethodArgumentNotValidException returns 400 with field errors")
    void handleValidationErrors_Returns400() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "personalAccessToken", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        // Use reflection to create a MethodArgumentNotValidException without needing a real MethodParameter
        // The handler only reads bindingResult.getFieldErrors(), so the MethodParameter is not used
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> result = handler.handleValidationErrors(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
    }

    // ── Generic exception tests ──

    @Test
    @DisplayName("IllegalArgumentException returns 400 BAD_REQUEST")
    void handleIllegalArgument_Returns400() {
        ResponseEntity<ApiResponse<?>> result =
                handler.handleIllegalArgument(new IllegalArgumentException("Bad input"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).contains("Bad input");
    }

    @Test
    @DisplayName("RuntimeException returns 500 INTERNAL_SERVER_ERROR")
    void handleRuntimeException_Returns500() {
        ResponseEntity<ApiResponse<?>> result =
                handler.handleRuntimeException(new RuntimeException("Something broke"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Exception returns 500 INTERNAL_SERVER_ERROR")
    void handleGenericException_Returns500() {
        ResponseEntity<ApiResponse<?>> result =
                handler.handleGenericException(new Exception("Unexpected error"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
    }
}
