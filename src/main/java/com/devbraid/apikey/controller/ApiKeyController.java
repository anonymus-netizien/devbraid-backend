package com.devbraid.apikey.controller;

import com.devbraid.apikey.entity.ApiKey;
import com.devbraid.apikey.service.ApiKeyService;
import com.devbraid.common.ApiResponse;
import com.devbraid.user.entity.User;
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
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
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
    public ResponseEntity<ApiResponse<List<ApiKey>>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success("API keys retrieved", apiKeyService.listKeys(user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal User user, @PathVariable UUID id) {
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
