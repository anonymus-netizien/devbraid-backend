package com.devbraid.user;

import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.user.dto.LoginRequest;
import com.devbraid.user.dto.LoginResponse;
import com.devbraid.user.dto.RefreshTokenRequest;
import com.devbraid.user.dto.RegisterRequest;
import com.devbraid.user.dto.UserProfileResponse;
import com.devbraid.user.exception.UserAlreadyExistsException;
import com.devbraid.user.exception.UserNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AuthController Unit Tests")
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    private AuthController authController;

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";
    private static final String FULL_NAME = "John Doe";
    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String REFRESH_TOKEN = "refresh-token-value";

    @BeforeEach
    void setUp() {
        authController = new AuthController(userService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- Register Tests ---

    @Test
    @DisplayName("POST /api/v1/auth/register returns 201 CREATED with success message")
    void register_Returns201() throws Exception {
        doNothing().when(userService).register(any(RegisterRequest.class));

        String body = objectMapper.writeValueAsString(
                new RegisterRequest(FULL_NAME, EMAIL, PASSWORD)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(userService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 409 CONFLICT when email already exists")
    void register_DuplicateEmail_Returns409() throws Exception {
        doThrow(new UserAlreadyExistsException("Email already registered"))
                .when(userService).register(any(RegisterRequest.class));

        String body = objectMapper.writeValueAsString(
                new RegisterRequest(FULL_NAME, EMAIL, PASSWORD)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 when email is invalid")
    void register_InvalidEmail_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest(FULL_NAME, "invalid-email", PASSWORD)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 when password is too short")
    void register_ShortPassword_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest(FULL_NAME, EMAIL, "short")
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // --- Login Tests ---

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200 OK with LoginResponse")
    void login_Returns200WithLoginResponse() throws Exception {
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken(ACCESS_TOKEN)
                .refreshToken(REFRESH_TOKEN)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .email(EMAIL)
                .userId(USER_ID)
                .role("DEVELOPER")
                .build();

        when(userService.login(EMAIL, PASSWORD)).thenReturn(loginResponse);

        String body = objectMapper.writeValueAsString(
                new LoginRequest(EMAIL, PASSWORD)
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").value(REFRESH_TOKEN))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.issuedAt").exists())
                .andExpect(jsonPath("$.data.expiresAt").exists());

        verify(userService).login(EMAIL, PASSWORD);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 400 when email is empty")
    void login_EmptyEmail_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new LoginRequest("", PASSWORD)
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // --- Me (Profile) Tests ---

    @Test
    @DisplayName("GET /api/v1/auth/me returns 200 OK with user profile")
    void me_Returns200WithProfile() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(USER_ID.toString(), null, "ROLE_DEVELOPER"));

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(USER_ID)
                .fullName(FULL_NAME)
                .email(EMAIL)
                .role("DEVELOPER")
                .createdAt(OffsetDateTime.now())
                .build();

        when(userService.getUserProfile(USER_ID.toString())).thenReturn(profile);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User profile retrieved"))
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.fullName").value(FULL_NAME))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.createdAt").exists());

        verify(userService).getUserProfile(USER_ID.toString());
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/auth/me returns 404 when user not found")
    void me_UserNotFound_Returns404() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(USER_ID.toString(), null, "ROLE_DEVELOPER"));

        when(userService.getUserProfile(USER_ID.toString()))
                .thenThrow(new UserNotFoundException("User not found with id: " + USER_ID));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User not found with id: " + USER_ID));

        SecurityContextHolder.clearContext();
    }

    // --- Refresh Token Tests ---

    @Test
    @DisplayName("POST /api/v1/auth/refresh returns 200 OK with new tokens")
    void refresh_Returns200WithNewTokens() throws Exception {
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .email(EMAIL)
                .userId(USER_ID)
                .role("DEVELOPER")
                .build();

        when(userService.refreshToken(REFRESH_TOKEN)).thenReturn(loginResponse);

        String body = objectMapper.writeValueAsString(
                new RefreshTokenRequest(REFRESH_TOKEN)
        );

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token refreshed successfully"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));

        verify(userService).refreshToken(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh returns 400 when token is blank")
    void refresh_BlankToken_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RefreshTokenRequest("")
        );

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // --- Logout Tests ---

    @Test
    @DisplayName("POST /api/v1/auth/logout returns 200 OK with success message")
    void logout_Returns200() throws Exception {
        doNothing().when(userService).logout(REFRESH_TOKEN);

        String body = objectMapper.writeValueAsString(
                new RefreshTokenRequest(REFRESH_TOKEN)
        );

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(userService).logout(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout returns 400 when token is blank")
    void logout_BlankToken_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RefreshTokenRequest("")
        );

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout returns 401 when token is already revoked")
    void logout_RevokedToken_Returns401() throws Exception {
        doThrow(new com.devbraid.user.exception.RefreshTokenRevokedException("Refresh token has been revoked"))
                .when(userService).logout(REFRESH_TOKEN);

        String body = objectMapper.writeValueAsString(
                new RefreshTokenRequest(REFRESH_TOKEN)
        );

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
