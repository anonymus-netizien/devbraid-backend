package com.devbraid.apikey.controller;

import com.devbraid.apikey.entity.ApiKey;
import com.devbraid.apikey.service.ApiKeyService;
import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Manage API keys for machine-to-machine access. Full key returned exactly once.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Keys", description = "Manage API keys for machine-to-machine access to the API (sent as the `X-API-Key` header). The full key value is returned exactly once, at creation.")
@SecurityRequirement(name = "bearer-jwt")
@SecurityRequirement(name = "api-key")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @Operation(
            summary = "Create an API key",
            description = "Creates an API key with an optional set of scopes and a rate limit. **The full key is returned only in this response** — store it immediately."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "API key created — full key visible only here",
            content = @Content(schema = @Schema(implementation = ApiKeyService.CreatedKey.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<ApiKeyService.CreatedKey>> create(
            @AuthenticationPrincipal User user,
            @RequestBody CreateApiKeyRequest request) {
        ApiKeyService.CreatedKey created = apiKeyService.createKey(
                user, request.name(), request.scopes(), request.rateLimitPerMin());
        log.info("Created API key '{}' for user {}", request.name(), user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("API key created — store the full key now, it is shown only once", created));
    }

    @GetMapping
    @Operation(
            summary = "List API keys",
            description = "Lists the authenticated user's API keys (metadata only — never the full key)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "API keys retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiKey.class))))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<List<ApiKey>>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success("API keys retrieved", apiKeyService.listKeys(user)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete an API key",
            description = "Permanently revokes an API key. Requests authenticated with it fail afterwards."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "API key deleted",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal User user,
                                                    @PathVariable @Parameter(description = "API key ID") UUID id) {
        apiKeyService.deleteKey(user, id);
        return ResponseEntity.ok(ApiResponse.success("API key deleted"));
    }

    public record CreateApiKeyRequest(
            @Size(max = 100, message = "name must be at most 100 characters") String name,
            List<String> scopes,
            Integer rateLimitPerMin
    ) {
    }
}
