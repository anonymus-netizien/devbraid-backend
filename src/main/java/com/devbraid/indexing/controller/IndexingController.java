package com.devbraid.indexing.controller;

import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.indexing.entity.CodebaseIndex;
import com.devbraid.indexing.entity.FileIndex;
import com.devbraid.indexing.service.IndexingService;
import com.devbraid.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/indexing")
@RequiredArgsConstructor
@Tag(name = "Codebase Indexing", description = "Index a repository's file contents for structural analysis (AST parsing) and query the resulting dependency graph, files and search.")
@SecurityRequirement(name = "bearer-jwt")
@SecurityRequirement(name = "api-key")
public class IndexingController {

    private final IndexingService indexingService;

    @PostMapping("/start")
    @Operation(
            summary = "Start indexing a codebase",
            description = "Creates a codebase index for a repository/branch. When `fileContents` is provided, indexing runs immediately; otherwise the index is created empty and can be indexed later."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Indexing started",
            content = @Content(schema = @Schema(implementation = CodebaseIndex.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<CodebaseIndex>> startIndexing(
            @AuthenticationPrincipal User user,
            @RequestBody StartIndexRequest request) {
        CodebaseIndex index = indexingService.createIndex(user, request.repository(), request.branch());
        if (request.fileContents() != null && !request.fileContents().isEmpty()) {
            indexingService.startIndexing(index.getId(), request.fileContents());
        }
        return ResponseEntity.ok(ApiResponse.success("Indexing started", index));
    }

    @GetMapping("/{indexId}")
    @Operation(
            summary = "Get an index",
            description = "Returns index metadata and processing status by ID."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Index found",
            content = @Content(schema = @Schema(implementation = CodebaseIndex.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<CodebaseIndex>> getIndex(
            @PathVariable @Parameter(description = "Index ID") UUID indexId) {
        return indexingService.getIndex(indexId)
                .map(idx -> ResponseEntity.ok(ApiResponse.success("Index found", idx)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    @Operation(
            summary = "List indexes",
            description = "Lists the authenticated user's codebase indexes."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Indexes retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CodebaseIndex.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<CodebaseIndex>>> listIndexes(
            @AuthenticationPrincipal User user) {
        List<CodebaseIndex> indexes = indexingService.listIndexesByUser(user);
        return ResponseEntity.ok(ApiResponse.success("Indexes retrieved", indexes));
    }

    @GetMapping("/{indexId}/files")
    @Operation(
            summary = "List indexed files",
            description = "Returns the files parsed for an index."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Files retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileIndex.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<FileIndex>>> getFiles(
            @PathVariable @Parameter(description = "Index ID") UUID indexId) {
        List<FileIndex> files = indexingService.getFilesByIndex(indexId);
        return ResponseEntity.ok(ApiResponse.success("Files retrieved", files));
    }

    @GetMapping("/{indexId}/search")
    @Operation(
            summary = "Search indexed files",
            description = "Searches indexed files by name/content pattern. Accepts `pattern` (or alias `q`)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileIndex.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<FileIndex>>> searchFiles(
            @PathVariable @Parameter(description = "Index ID") UUID indexId,
            @RequestParam(defaultValue = "") @Parameter(description = "Search pattern", example = "JwtTokenProvider") String pattern,
            @RequestParam(required = false) @Parameter(description = "Alias for `pattern`") String q) {
        // Support both 'pattern' and 'q' for consistency with other search endpoints
        String searchTerm = (q != null && !q.isBlank()) ? q : pattern;
        List<FileIndex> files = indexingService.searchFiles(indexId, searchTerm);
        return ResponseEntity.ok(ApiResponse.success("Search results", files));
    }

    @GetMapping("/{indexId}/language/{language}")
    @Operation(
            summary = "List files by language",
            description = "Returns indexed files filtered by programming language, e.g. `java`."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Files by language",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileIndex.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<FileIndex>>> getFilesByLanguage(
            @PathVariable @Parameter(description = "Index ID") UUID indexId,
            @PathVariable @Parameter(description = "Language", example = "java") String language) {
        List<FileIndex> files = indexingService.getFilesByLanguage(indexId, language);
        return ResponseEntity.ok(ApiResponse.success("Files by language", files));
    }

    @GetMapping("/{indexId}/graph")
    @Operation(
            summary = "Get dependency graph",
            description = "Returns the dependency graph (nodes + edges) of an index, optionally rooted at a node and bounded by `depth` (1–10)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dependency graph retrieved",
            content = @Content(schema = @Schema(implementation = IndexingService.DependencyGraphResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<IndexingService.DependencyGraphResponse>> getGraph(
            @PathVariable @Parameter(description = "Index ID") UUID indexId,
            @RequestParam(required = false) @Parameter(description = "Root node ID — whole graph when omitted") UUID nodeId,
            @RequestParam(defaultValue = "3") @Parameter(description = "Traversal depth (clamped to 1–10)", example = "3") int depth) {
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
