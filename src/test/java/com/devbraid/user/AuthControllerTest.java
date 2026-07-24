package com.devbraid.user;

import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.user.dto.LoginRequest;
import com.devbraid.user.dto.LoginResponse;
import com.devbraid.user.dto.SignupRequest;
import com.devbraid.user.exception.UserAlreadyExistsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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

    @Test
    @DisplayName("POST /api/auth/signup returns 201 CREATED with success message")
    void signup_Returns201() throws Exception {
        doNothing().when(userService).signup(any(SignupRequest.class));

        String body = objectMapper.writeValueAsString(
                new SignupRequest(EMAIL, PASSWORD)
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Signup successful"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(userService).signup(any(SignupRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signup returns 409 CONFLICT when email already exists")
    void signup_DuplicateEmail_Returns409() throws Exception {
        doThrow(new UserAlreadyExistsException("Email already registered"))
                .when(userService).signup(any(SignupRequest.class));

        String body = objectMapper.writeValueAsString(
                new SignupRequest(EMAIL, PASSWORD)
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("POST /api/auth/signup returns 400 when email is invalid")
    void signup_InvalidEmail_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new SignupRequest("invalid-email", PASSWORD)
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup returns 400 when password is too short")
    void signup_ShortPassword_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new SignupRequest(EMAIL, "short")
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 OK with LoginResponse")
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

        mockMvc.perform(post("/api/auth/login")
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
    @DisplayName("POST /api/auth/login returns 400 when email is empty")
    void login_EmptyEmail_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new LoginRequest("", PASSWORD)
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
