package com.devbraid.apikey.service;

import com.devbraid.apikey.entity.ApiKey;
import com.devbraid.apikey.repository.ApiKeyRepository;
import com.devbraid.apikey.repository.ApiUsageRepository;
import com.devbraid.apikey.security.InMemoryRateLimiter;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ApiKeyService Unit Tests")
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ApiUsageRepository apiUsageRepository;

    private ApiKeyService apiKeyService;
    private User testUser;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository, apiUsageRepository, new InMemoryRateLimiter());
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .build();
    }

    @Test
    @DisplayName("createKey returns full key once and stores only its SHA-256 hash")
    void createKey_returnsFullKeyAndStoresHash() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.CreatedKey created = apiKeyService.createKey(testUser, "ci", List.of("analyze"), 30);

        assertTrue(created.fullKey().startsWith("db_live_"));
        assertEquals(8 + 64, created.fullKey().length(), "prefix (8) + 64 hex chars");

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();
        assertNotEquals(created.fullKey(), saved.getKeyHash(), "plaintext key must never be stored");
        assertEquals(64, saved.getKeyHash().length(), "SHA-256 hex hash is 64 chars");
        assertEquals("db_live_", saved.getPrefix());
    }

    @Test
    @DisplayName("validate returns the key for a stored full key, null for unknown/inactive")
    void validate_matchesHash() {
        ApiKeyService.CreatedKey created = apiKeyService.createKey(testUser, "ci", List.of(), 60);
        String hash = sha256Hex(created.fullKey());

        ApiKey stored = ApiKey.builder()
                .id(created.id())
                .user(testUser)
                .name("ci")
                .prefix("db_live_")
                .keyHash(hash)
                .active(true)
                .build();
        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(stored));

        assertEquals(stored, apiKeyService.validate(created.fullKey()));
        assertNull(apiKeyService.validate("db_live_doesnotexist"));
        assertNull(apiKeyService.validate("not-a-key"));
    }

    @Test
    @DisplayName("rate limiter allows limitPerMin requests then blocks")
    void rateLimit_blocksAfterLimit() {
        ApiKey key = ApiKey.builder()
                .keyHash("k")
                .rateLimitPerMin(2)
                .build();

        assertTrue(apiKeyService.isAllowed(key));
        assertTrue(apiKeyService.isAllowed(key));
        assertFalse(apiKeyService.isAllowed(key));
    }

    private String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
