package com.devbraid.user.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.user.dto.request.*;
import com.devbraid.user.dto.response.LoginResponse;
import com.devbraid.user.dto.response.OtpSendResponse;
import com.devbraid.user.dto.response.OtpVerifyResponse;
import com.devbraid.user.dto.response.UserProfileResponse;
import com.devbraid.user.entity.User;
import com.devbraid.user.service.OtpService;
import com.devbraid.user.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final OtpService otpService;

    public AuthController(UserService userService, OtpService otpService) {
        this.userService = userService;
        this.otpService = otpService;
    }

    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<OtpSendResponse>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        log.info("AuthController :: Received OTP send request for: {}", request.getEmail());
        otpService.generateAndStoreOtp(request.getEmail());
        OtpSendResponse response = OtpSendResponse.builder()
                .email(request.getEmail())
                .build();
        return ResponseEntity.ok(ApiResponse.success("OTP sent", response));
    }

    @PostMapping("/otp/verify")
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
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("AuthController :: Received register request for: {}", request.getEmail());
        userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("AuthController :: Received login request for: {}", request.getEmail());
        LoginResponse response = userService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(@AuthenticationPrincipal User user) {
        log.info("AuthController :: Fetching profile for user id: {}", user.getId());
        UserProfileResponse profile = userService.getUserProfile(user.getId().toString());
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved", profile));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("AuthController :: Token refresh request received");
        LoginResponse response = userService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("AuthController :: Logout request");
        userService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
}
