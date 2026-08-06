package com.devbraid.github.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.github.dto.request.ConnectRequest;
import com.devbraid.github.dto.response.BranchDto;
import com.devbraid.github.dto.response.GitHubStatusResponse;
import com.devbraid.github.dto.response.GitRepositoryDto;
import com.devbraid.github.service.GitHubConnectionService;
import com.devbraid.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "GitHub Connection", description = "Connect a GitHub account with a personal access token, then browse the connected account's repositories and branches.")
@SecurityRequirement(name = "bearer-jwt")
public class GitHubConnectionController {

    private final GitHubConnectionService gitHubConnectionService;

    @PostMapping("/connect")
    @Operation(
            summary = "Connect GitHub account",
            description = "Stores a GitHub personal access token for the authenticated user. Fails with 409 when an account is already connected."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "GitHub connected",
            content = @Content(schema = @Schema(implementation = GitHubStatusResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "GitHub account already connected",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<GitHubStatusResponse>> connect(
            @Valid @RequestBody ConnectRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Connecting GitHub for user {}", user.getEmail());
        GitHubStatusResponse response = gitHubConnectionService.connect(
                request.getPersonalAccessToken(), user
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("GitHub connected successfully", response));
    }

    @DeleteMapping("/disconnect")
    @Operation(
            summary = "Disconnect GitHub account",
            description = "Removes the stored GitHub personal access token for the authenticated user."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GitHub disconnected",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Void>> disconnect(
            @AuthenticationPrincipal User user) {
        log.info("Disconnecting GitHub for user {}", user.getEmail());
        gitHubConnectionService.disconnect(user);
        return ResponseEntity.ok(ApiResponse.success("GitHub disconnected", null));
    }

    @GetMapping("/status")
    @Operation(
            summary = "Get GitHub connection status",
            description = "Returns whether the user has connected a GitHub account and the connected user's details."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Connection status retrieved",
            content = @Content(schema = @Schema(implementation = GitHubStatusResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<GitHubStatusResponse>> getStatus(
            @AuthenticationPrincipal User user) {
        GitHubStatusResponse response = gitHubConnectionService.getStatus(user);
        return ResponseEntity.ok(ApiResponse.success("Connection status retrieved", response));
    }

    @GetMapping("/repos")
    @Operation(
            summary = "List connected repositories",
            description = "Lists repositories accessible to the connected GitHub account (token scopes permitting)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Repositories retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GitRepositoryDto.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<GitRepositoryDto>>> listRepositories(
            @AuthenticationPrincipal User user) {
        log.info("Listing repositories for user {}", user.getEmail());
        List<GitRepositoryDto> repos = gitHubConnectionService.listRepositories(user);
        return ResponseEntity.ok(ApiResponse.success("Repositories retrieved", repos));
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    @Operation(
            summary = "List repository branches (path params)",
            description = "Lists branches of `owner/repo` using path parameters. A query-param overload is available at `GET /github/branches`."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Branches retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BranchDto.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<BranchDto>>> listBranches(
            @PathVariable @Parameter(description = "Repository owner", example = "anonymus-netizien") String owner,
            @PathVariable @Parameter(description = "Repository name", example = "devbraid-backend") String repo,
            @AuthenticationPrincipal User user) {
        log.info("Listing branches for {}/{} by user {}", owner, repo, user.getEmail());
        List<BranchDto> branches = gitHubConnectionService.listBranches(user, owner, repo);
        return ResponseEntity.ok(ApiResponse.success("Branches retrieved", branches));
    }

    /**
     * Query-param overload for listBranches — improves API usability for frontend consumers.
     */
    @GetMapping("/branches")
    @Operation(
            summary = "List repository branches (query params)",
            description = "Query-parameter overload of `GET /github/repos/{owner}/{repo}/branches`."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Branches retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BranchDto.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<BranchDto>>> listBranchesByQuery(
            @RequestParam @Parameter(description = "Repository owner", example = "anonymus-netizien") String owner,
            @RequestParam @Parameter(description = "Repository name", example = "devbraid-backend") String repo,
            @AuthenticationPrincipal User user) {
        log.info("Listing branches for {}/{} by user {} (query params)", owner, repo, user.getEmail());
        List<BranchDto> branches = gitHubConnectionService.listBranches(user, owner, repo);
        return ResponseEntity.ok(ApiResponse.success("Branches retrieved", branches));
    }
}
