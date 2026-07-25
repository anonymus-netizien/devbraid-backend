package com.devbraid.user;

import com.devbraid.common.ApiResponse;
import com.devbraid.user.dto.LoginRequest;
import com.devbraid.user.dto.LoginResponse;
import com.devbraid.user.dto.RefreshTokenRequest;
import com.devbraid.user.dto.RegisterRequest;
import com.devbraid.user.dto.UserProfileResponse;
import com.devbraid.user.otp.OtpSendRequest;
import com.devbraid.user.otp.OtpSendResponse;
import com.devbraid.user.otp.OtpService;
import com.devbraid.user.otp.OtpVerifyRequest;
import com.devbraid.user.otp.OtpVerifyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final OtpService otpService;

    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<OtpSendResponse>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        log.info("AuthController :: Received OTP send request for: {}", request.getEmail());
        otpService.generateAndStoreOtp(request.getEmail());
        OtpSendResponse response = OtpSendResponse.builder()
                .email(request.getEmail())
                .message("OTP sent successfully")
                .sent(true)
                .build();
        return ResponseEntity.ok(ApiResponse.success("OTP sent", response));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        log.info("AuthController :: Received OTP verify request for: {}", request.getEmail());
        otpService.verifyOtp(request.getEmail(), request.getOtp());
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
    public ResponseEntity<ApiResponse<UserProfileResponse>> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();
        log.info("AuthController :: Fetching profile for user id: {}", userId);
        UserProfileResponse profile = userService.getUserProfile(userId);
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
