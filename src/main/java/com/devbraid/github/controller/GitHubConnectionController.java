package com.devbraid.github.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.github.dto.request.ConnectRequest;
import com.devbraid.github.dto.response.BranchDto;
import com.devbraid.github.dto.response.GitHubStatusResponse;
import com.devbraid.github.dto.response.GitRepositoryDto;
import com.devbraid.github.service.GitHubConnectionService;
import com.devbraid.user.entity.User;
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
public class GitHubConnectionController {

    private final GitHubConnectionService gitHubConnectionService;

    @PostMapping("/connect")
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
    public ResponseEntity<ApiResponse<Void>> disconnect(
            @AuthenticationPrincipal User user) {
        log.info("Disconnecting GitHub for user {}", user.getEmail());
        gitHubConnectionService.disconnect(user);
        return ResponseEntity.ok(ApiResponse.success("GitHub disconnected", null));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<GitHubStatusResponse>> getStatus(
            @AuthenticationPrincipal User user) {
        GitHubStatusResponse response = gitHubConnectionService.getStatus(user);
        return ResponseEntity.ok(ApiResponse.success("Connection status retrieved", response));
    }

    @GetMapping("/repos")
    public ResponseEntity<ApiResponse<List<GitRepositoryDto>>> listRepositories(
            @AuthenticationPrincipal User user) {
        log.info("Listing repositories for user {}", user.getEmail());
        List<GitRepositoryDto> repos = gitHubConnectionService.listRepositories(user);
        return ResponseEntity.ok(ApiResponse.success("Repositories retrieved", repos));
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<ApiResponse<List<BranchDto>>> listBranches(
            @PathVariable String owner,
            @PathVariable String repo,
            @AuthenticationPrincipal User user) {
        log.info("Listing branches for {}/{} by user {}", owner, repo, user.getEmail());
        List<BranchDto> branches = gitHubConnectionService.listBranches(user, owner, repo);
        return ResponseEntity.ok(ApiResponse.success("Branches retrieved", branches));
    }
}
