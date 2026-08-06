package com.devbraid.user.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.user.dto.request.UpdatePasswordRequest;
import com.devbraid.user.dto.request.UpdateProfileRequest;
import com.devbraid.user.dto.response.UserProfileResponse;
import com.devbraid.user.entity.User;
import com.devbraid.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "User", description = "Current user's profile and password management (JWT required).")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {

    private final UserService userService;

    /**
     * Get current user's profile.
     */
    @GetMapping("/profile")
    @Operation(
            summary = "Get current user profile",
            description = "Returns the profile of the authenticated user (id, full name, email, avatar, GitHub connection status)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved",
            content = @Content(schema = @Schema(implementation = UserProfileResponse.class)))
    @ApiErrorResponses
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
    @Operation(
            summary = "Update current user profile",
            description = "Updates the profile of the authenticated user. Fields that are null or blank are left unchanged."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(schema = @Schema(implementation = UserProfileResponse.class)))
    @ApiErrorResponses
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
    @Operation(
            summary = "Change password",
            description = "Changes the password of the authenticated user. Requires the current password; fails with 400 when the current password is incorrect or the new one is too weak."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal User user) {
        log.info("UserController :: Changing password for user {}", user.getEmail());
        userService.changePassword(user, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed", null));
    }
}
