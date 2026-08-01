package com.devbraid.user.controller;

import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.user.dto.request.*;
import com.devbraid.user.dto.response.LoginResponse;
import com.devbraid.user.exception.UserAlreadyExistsException;
import com.devbraid.user.service.OtpService;
import com.devbraid.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AuthController Unit Tests")
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";
    private static final String FULL_NAME = "John Doe";
    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    @Mock
    private UserService userService;
    @Mock
    private OtpService otpService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(userService, otpService);
        ReflectionTestUtils.setField(authController, "refreshExpirationMs", 604800000L);
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/auth/otp/send returns 200 OK")
    void sendOtp_Returns200() throws Exception {
        doNothing().when(otpService).generateAndStoreOtp(EMAIL);

        String body = objectMapper.writeValueAsString(new OtpSendRequest(EMAIL));

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(EMAIL));

        verify(otpService).generateAndStoreOtp(EMAIL);
    }

    @Test
    @DisplayName("POST /api/v1/auth/otp/verify returns 200 OK")
    void verifyOtp_Returns200() throws Exception {
        doNothing().when(otpService).verifyOtp(EMAIL, "123456");
        doNothing().when(userService).finalizeRegistration(EMAIL);

        String body = objectMapper.writeValueAsString(new OtpVerifyRequest(EMAIL, "123456"));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true));

        verify(userService).finalizeRegistration(EMAIL);
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 201 CREATED")
    void register_Returns201() throws Exception {
        doNothing().when(userService).register(any(RegisterRequest.class));

        String body = objectMapper.writeValueAsString(new RegisterRequest(FULL_NAME, EMAIL, PASSWORD));

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

        String body = objectMapper.writeValueAsString(new RegisterRequest(FULL_NAME, EMAIL, PASSWORD));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200 OK with accessToken in body and refreshToken in httpOnly cookie")
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

        String body = objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.issuedAt").exists())
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    assertTrue(setCookie != null && setCookie.contains("refreshToken="));
                    assertTrue(setCookie.contains("HttpOnly"));
                    assertTrue(setCookie.contains("Secure"));
                    assertTrue(setCookie.contains("SameSite=Lax"));
                });

        verify(userService).login(EMAIL, PASSWORD);
    }

    // NOTE: GET /auth/me test removed — endpoint moved to UserController /api/v1/user/profile
    // See UserControllerTest for profile endpoint tests

    @Test
    @DisplayName("POST /api/v1/auth/refresh returns 200 OK with new accessToken in body and new refreshToken in httpOnly cookie")
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

        String body = objectMapper.writeValueAsString(new RefreshTokenRequest(REFRESH_TOKEN));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token refreshed successfully"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    assert setCookie != null && setCookie.contains("refreshToken=new-refresh-token");
                    assertTrue(setCookie.contains("HttpOnly"));
                    assertTrue(setCookie.contains("Secure"));
                });

        verify(userService).refreshToken(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout returns 200 OK")
    void logout_Returns200() throws Exception {
        doNothing().when(userService).logout(REFRESH_TOKEN);

        String body = objectMapper.writeValueAsString(new RefreshTokenRequest(REFRESH_TOKEN));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(userService).logout(REFRESH_TOKEN);
    }
}
