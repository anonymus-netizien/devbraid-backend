package com.devbraid.user.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.security.CookieUtils;
import com.devbraid.user.dto.request.*;
import com.devbraid.user.dto.response.LoginResponse;
import com.devbraid.user.dto.response.OtpSendResponse;
import com.devbraid.user.dto.response.OtpVerifyResponse;
import com.devbraid.user.service.AuthRateLimiter;
import com.devbraid.user.service.OtpService;
import com.devbraid.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Public endpoints for registration, OTP verification, login, token refresh and logout. Login and refresh set the refresh token as an httpOnly cookie (path `/api/v1/auth/refresh`); the body always carries only the access token.")
public class AuthController {

    private final UserService userService;
    private final OtpService otpService;
    private final AuthRateLimiter authRateLimiter;

    @Value("${app.jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    public AuthController(UserService userService, OtpService otpService, AuthRateLimiter authRateLimiter) {
        this.userService = userService;
        this.otpService = otpService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/otp/send")
    @Operation(
            summary = "Send one-time password",
            description = "Generates an OTP for the given email and sends it via email. Rate limited per email address (429 when exceeded)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP sent",
            content = @Content(schema = @Schema(implementation = OtpSendResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many OTP requests — rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<OtpSendResponse>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        log.info("AuthController :: Received OTP send request for: {}", request.getEmail());
        otpService.generateAndStoreOtp(request.getEmail());
        OtpSendResponse response = OtpSendResponse.builder()
                .email(request.getEmail())
                .build();
        return ResponseEntity.ok(ApiResponse.success("OTP sent", response));
    }

    @PostMapping("/otp/verify")
    @Operation(
            summary = "Verify one-time password",
            description = "Validates the OTP for an email. When the email has a pending registration, verification finalizes it (creates the user) — this is the second step of the registration flow."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP verified",
            content = @Content(schema = @Schema(implementation = OtpVerifyResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "OTP expired",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        log.info("AuthController :: Received OTP verify request for: {}", request.getEmail());
        otpService.verifyOtp(request.getEmail(), request.getOtp());

        // If there's a pending registration in Redis, finalize it to PostgreSQL
        userService.finalizeRegistration(request.getEmail());

        OtpVerifyResponse response = OtpVerifyResponse.builder()
                .email(request.getEmail())
                .verified(true)
                .build();
        return ResponseEntity.ok(ApiResponse.success("OTP verified", response));
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Registers a user with full name, email and password. Registration is finalized only after the email is verified via `POST /auth/otp/verify`."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Registration accepted",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User with this email already exists",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many registration attempts — rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        log.info("AuthController :: Received register request for: {}", request.getEmail());
        authRateLimiter.checkRegister(request.getEmail(), httpRequest.getRemoteAddr());
        userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", null));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in",
            description = "Authenticates with email + password. Returns the access token in the response body and sets the refresh token as an httpOnly `refreshToken` cookie scoped to `/api/v1/auth/refresh`."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful — access token in body, refresh token in cookie",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many login attempts — rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        log.info("AuthController :: Received login request for: {}", request.getEmail());
        authRateLimiter.checkLogin(request.getEmail(), httpRequest.getRemoteAddr());
        LoginResponse response = userService.login(request.getEmail(), request.getPassword());

        // Set refresh token as httpOnly cookie
        long maxAgeSeconds = refreshExpirationMs / 1000;
        CookieUtils.addRefreshTokenCookie(httpResponse, response.getRefreshToken(), maxAgeSeconds);

        // Remove refresh token from response body (only keep access token)
        response.setRefreshToken(null);

        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // NOTE: GET /me moved to UserController /api/v1/user/profile
    // Profile and password endpoints also moved to UserController for cleaner separation

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Rotates the refresh token and issues a new access token. The refresh token may be supplied in the request body (`refreshToken` field) or in the `refreshToken` httpOnly cookie. A new refresh-token cookie is set and the old token is invalidated."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token rotated — new access token in body, new refresh token in cookie",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        log.info("AuthController :: Token refresh request received");

        // Get refresh token from request body or cookie
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = CookieUtils.extractRefreshTokenFromCookie(httpRequest);
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh token required"));
        }

        LoginResponse response = userService.refreshToken(refreshToken);

        // Set new refresh token as httpOnly cookie
        long maxAgeSeconds = refreshExpirationMs / 1000;
        CookieUtils.addRefreshTokenCookie(httpResponse, response.getRefreshToken(), maxAgeSeconds);

        // Remove refresh token from response body
        response.setRefreshToken(null);

        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }


    @PostMapping("/logout")
    @Operation(
            summary = "Log out",
            description = "Revokes the refresh token (from body or cookie) and clears the `refreshToken` cookie. Idempotent — logging out without a token still succeeds and clears the cookie."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out — refresh token revoked and cookie cleared",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        log.info("AuthController :: Logout request");

        // Get refresh token from request body or cookie
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = CookieUtils.extractRefreshTokenFromCookie(httpRequest);
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            userService.logout(refreshToken);
        }

        // Clear the refresh token cookie
        CookieUtils.clearRefreshTokenCookie(httpResponse);

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
}
