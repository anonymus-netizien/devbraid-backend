package com.devbraid.githubapp.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.githubapp.service.GitHubOAuthService;
import com.devbraid.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * GitHub App OAuth identity linking endpoints.
 * <p>
 * {@code /start} is Bearer-authenticated and returns the GitHub authorize URL in
 * the {@link ApiResponse} envelope (not a 302) so the frontend can open it in the
 * same tab. {@code /callback} is public — GitHub redirects the browser to it, so
 * it responds with a 302 to the frontend, not a JSON envelope.
 */
@RestController
@RequestMapping("/api/v1/github-app/oauth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "GitHub App OAuth", description = "Links a DevBraid account to a GitHub identity so GitHub App installations can be attributed to users.")
public class GitHubOAuthController {

    private final GitHubOAuthService oauthService;

    @GetMapping("/start")
    @Operation(
            summary = "Start GitHub OAuth identity linking",
            description = "Generates a one-time state nonce (Redis, 10-min TTL) and returns the GitHub authorize URL for the authenticated user."
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponse(responseCode = "200", description = "Authorize URL generated",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "503", description = "GitHub OAuth not configured",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<String>> startOAuth(@AuthenticationPrincipal User user) {
        String authorizeUrl = oauthService.startOAuth(user);
        return ResponseEntity.ok(ApiResponse.success("GitHub OAuth URL generated", authorizeUrl));
    }

    @GetMapping("/callback")
    @Operation(
            summary = "GitHub OAuth callback",
            description = "Public endpoint GitHub redirects the browser to after authorize. Validates and consumes the state, exchanges the code for an access token, fetches the GitHub user, upserts the identity, then 302-redirects to the frontend."
    )
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state) {
        String redirectUrl = oauthService.completeOAuth(code, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }
}
