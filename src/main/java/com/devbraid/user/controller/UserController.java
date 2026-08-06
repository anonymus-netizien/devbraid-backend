package com.devbraid.user.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
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
 * Current user's profile. Identity and credentials live in Clerk; this module
 * only manages the local profile mirror (full name) used by the five-step flow.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "Current user's profile (Clerk JWT required).")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    @Operation(
            summary = "Get current user profile",
            description = "Returns the profile of the authenticated Clerk user (id, full name, email)."
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
}
