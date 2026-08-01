package com.devbraid.apikey.service;

import com.devbraid.apikey.entity.ApiKey;
import com.devbraid.apikey.entity.ApiUsage;
import com.devbraid.apikey.repository.ApiKeyRepository;
import com.devbraid.apikey.repository.ApiUsageRepository;
import com.devbraid.apikey.security.InMemoryRateLimiter;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devbraid.security.SecurityUtils;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * API key lifecycle: create (full key shown once), validate, list, delete, rate-limit.
 * Only the SHA-256 hex hash is stored — the plaintext key never touches the DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String PREFIX = "db_live_";
    private static final int SECRET_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final ApiUsageRepository apiUsageRepository;
    private final InMemoryRateLimiter rateLimiter;

    /**
     * Create a key for a user. Returns the full key ONCE — it cannot be recovered later.
     */
    @Transactional
    public CreatedKey createKey(User user, String name, List<String> scopes, Integer rateLimitPerMin) {
        String secret = randomSecret();
        String fullKey = PREFIX + secret;

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name(name)
                .prefix(PREFIX)
                .keyHash(sha256Hex(fullKey))
                .scopes(scopes == null ? List.of() : scopes)
                .rateLimitPerMin(rateLimitPerMin == null ? 60 : rateLimitPerMin)
                .active(true)
                .build();
        apiKeyRepository.save(apiKey);

        log.info("Created API key '{}' for user {}", name, user.getId());
        return new CreatedKey(apiKey.getId(), fullKey, apiKey.getPrefix(), apiKey.getRateLimitPerMin());
    }

    /**
     * Validate a presented key; returns the key entity when active and unexpired, else null.
     */
    @Transactional(readOnly = true)
    public ApiKey validate(String presentedKey) {
        if (presentedKey == null || !presentedKey.startsWith(PREFIX)) {
            return null;
        }
        ApiKey apiKey = apiKeyRepository.findByKeyHash(sha256Hex(presentedKey)).orElse(null);
        if (apiKey == null || !Boolean.TRUE.equals(apiKey.getActive())) {
            return null;
        }
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return null;
        }
        return apiKey;
    }

    /**
     * Rate-limit gate for an API-key-authenticated request.
     */
    public boolean isAllowed(ApiKey apiKey) {
        return rateLimiter.isAllowed(apiKey.getKeyHash(), apiKey.getRateLimitPerMin());
    }

    public long retryAfterSeconds(ApiKey apiKey) {
        return rateLimiter.retryAfterSeconds(apiKey.getKeyHash());
    }

    @Transactional
    public void recordUsage(ApiKey apiKey, String endpoint, String method, int statusCode, int responseTimeMs) {
        apiUsageRepository.save(ApiUsage.builder()
                .apiKey(apiKey)
                .endpoint(endpoint)
                .method(method)
                .statusCode(statusCode)
                .responseTimeMs(responseTimeMs)
                .build());
        apiKey.setLastUsedAt(OffsetDateTime.now());
        apiKeyRepository.save(apiKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> listKeys(User user) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional
    public void deleteKey(User user, UUID keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .filter(k -> k.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiKeyNotFoundException("API key not found"));
        apiKeyRepository.delete(key);
    }

    private String randomSecret() {
        byte[] bytes = new byte[SECRET_LENGTH];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256Hex(String input) {
        return SecurityUtils.sha256Hex(input);
    }

    public record CreatedKey(UUID id, String fullKey, String prefix, Integer rateLimitPerMin) {
    }
}
