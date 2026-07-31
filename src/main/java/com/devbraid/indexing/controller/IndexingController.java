package com.devbraid.indexing.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.indexing.entity.CodebaseIndex;
import com.devbraid.indexing.entity.FileIndex;
import com.devbraid.indexing.service.IndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/indexing")
@RequiredArgsConstructor
public class IndexingController {

    private final IndexingService indexingService;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<CodebaseIndex>> startIndexing(
            @AuthenticationPrincipal com.devbraid.user.entity.User user,
            @RequestBody StartIndexRequest request) {
        CodebaseIndex index = indexingService.createIndex(user, request.repository(), request.branch());
        if (request.fileContents() != null && !request.fileContents().isEmpty()) {
            indexingService.startIndexing(index.getId(), request.fileContents());
        }
        return ResponseEntity.ok(ApiResponse.success("Indexing started", index));
    }

    @GetMapping("/{indexId}")
    public ResponseEntity<ApiResponse<CodebaseIndex>> getIndex(@PathVariable UUID indexId) {
        return indexingService.getIndex(indexId)
                .map(idx -> ResponseEntity.ok(ApiResponse.success("Index found", idx)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<CodebaseIndex>>> listIndexes(
            @AuthenticationPrincipal com.devbraid.user.entity.User user) {
        List<CodebaseIndex> indexes = indexingService.listIndexesByUser(user);
        return ResponseEntity.ok(ApiResponse.success("Indexes retrieved", indexes));
    }

    @GetMapping("/{indexId}/files")
    public ResponseEntity<ApiResponse<List<FileIndex>>> getFiles(@PathVariable UUID indexId) {
        List<FileIndex> files = indexingService.getFilesByIndex(indexId);
        return ResponseEntity.ok(ApiResponse.success("Files retrieved", files));
    }

    @GetMapping("/{indexId}/search")
    public ResponseEntity<ApiResponse<List<FileIndex>>> searchFiles(
            @PathVariable UUID indexId,
            @RequestParam String pattern) {
        List<FileIndex> files = indexingService.searchFiles(indexId, pattern);
        return ResponseEntity.ok(ApiResponse.success("Search results", files));
    }

    @GetMapping("/{indexId}/language/{language}")
    public ResponseEntity<ApiResponse<List<FileIndex>>> getFilesByLanguage(
            @PathVariable UUID indexId,
            @PathVariable String language) {
        List<FileIndex> files = indexingService.getFilesByLanguage(indexId, language);
        return ResponseEntity.ok(ApiResponse.success("Files by language", files));
    }

    @GetMapping("/{indexId}/graph")
    public ResponseEntity<ApiResponse<IndexingService.DependencyGraphResponse>> getGraph(
            @PathVariable UUID indexId,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(defaultValue = "3") int depth) {
        // ponytail: clamp depth to a sane bound — deeper traversal is a slow query for no MVP value
        int safeDepth = Math.min(Math.max(depth, 1), 10);
        IndexingService.DependencyGraphResponse graph =
                indexingService.getDependencyGraph(indexId, nodeId, safeDepth);
        return ResponseEntity.ok(ApiResponse.success("Dependency graph retrieved", graph));
    }

    public record StartIndexRequest(
            String repository,
            String branch,
            List<String> fileContents
    ) {
    }
}
