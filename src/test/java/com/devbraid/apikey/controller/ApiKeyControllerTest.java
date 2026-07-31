package com.devbraid.apikey.controller;

import com.devbraid.apikey.entity.ApiKey;
import com.devbraid.apikey.service.ApiKeyNotFoundException;
import com.devbraid.apikey.service.ApiKeyService;
import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for ApiKeyController — verifies CRUD flows with @AuthenticationPrincipal.
 */
@DisplayName("ApiKeyController Unit Tests")
@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID KEY_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @Mock
    private ApiKeyService apiKeyService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ApiKeyController controller = new ApiKeyController(apiKeyService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        testUser = User.builder()
                .id(USER_ID)
                .email("test@example.com")
                .fullName("Test User")
                .build();

        setAuthentication(testUser);
    }

    @Test
    @DisplayName("POST /api/v1/api-keys creates key and returns 201")
    void create_validRequest_returnsCreated() throws Exception {
        ApiKeyService.CreatedKey created = new ApiKeyService.CreatedKey(
                KEY_ID, "db_live_abc123def456", "db_live_", 60);
        when(apiKeyService.createKey(eq(testUser), eq("ci-key"), any(), eq(60)))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ci-key\",\"scopes\":[\"analyze\"],\"rateLimitPerMin\":60}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(KEY_ID.toString()))
                .andExpect(jsonPath("$.data.fullKey").value("db_live_abc123def456"));
    }

    @Test
    @DisplayName("GET /api/v1/api-keys returns list of keys")
    void list_returnsKeys() throws Exception {
        ApiKey key = ApiKey.builder()
                .id(KEY_ID)
                .user(testUser)
                .name("my-key")
                .prefix("db_live_")
                .active(true)
                .rateLimitPerMin(60)
                .createdAt(OffsetDateTime.now())
                .build();
        when(apiKeyService.listKeys(testUser)).thenReturn(List.of(key));

        mockMvc.perform(get("/api/v1/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(KEY_ID.toString()));
    }

    @Test
    @DisplayName("DELETE /api/v1/api-keys/{id} deletes key and returns 200")
    void delete_existingKey_returnsOk() throws Exception {
        doNothing().when(apiKeyService).deleteKey(testUser, KEY_ID);

        mockMvc.perform(delete("/api/v1/api-keys/" + KEY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(apiKeyService).deleteKey(testUser, KEY_ID);
    }

    @Test
    @DisplayName("DELETE /api/v1/api-keys/{id} returns 404 when key not found")
    void delete_nonexistentKey_returnsNotFound() throws Exception {
        doThrow(new ApiKeyNotFoundException("API key not found"))
                .when(apiKeyService).deleteKey(testUser, KEY_ID);

        mockMvc.perform(delete("/api/v1/api-keys/" + KEY_ID))
                .andExpect(status().isNotFound());
    }

    private void setAuthentication(User user) {
        var auth = new TestingAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
