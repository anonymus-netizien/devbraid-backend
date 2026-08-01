package com.devbraid.user.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.user.dto.request.UpdateProfileRequest;
import com.devbraid.user.dto.request.UpdatePasswordRequest;
import com.devbraid.user.dto.response.UserProfileResponse;
import com.devbraid.user.entity.User;
import com.devbraid.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * User profile management endpoints.
 * Replaces auth/me — keeps auth module focused on authentication only.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get current user's profile.
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal User user) {
        log.info("UserController :: Fetching profile for user id: {}", user.getId());
        UserProfileResponse profile = userService.getUserProfile(user.getId().toString());
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved", profile));
    }

    /**
     * Update current user's profile.
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal User user) {
        log.info("UserController :: Updating profile for user {}", user.getEmail());
        UserProfileResponse profile = userService.updateProfile(user, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", profile));
    }

    /**
     * Change current user's password.
     */
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal User user) {
        log.info("UserController :: Changing password for user {}", user.getEmail());
        userService.changePassword(user, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed", null));
    }
}
