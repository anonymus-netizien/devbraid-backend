package com.devbraid.user.service;

import com.devbraid.security.JwtTokenProvider;
import com.devbraid.user.dto.request.RegisterRequest;
import com.devbraid.user.dto.response.LoginResponse;
import com.devbraid.user.entity.RefreshToken;
import com.devbraid.user.entity.User;
import com.devbraid.user.exception.InvalidCredentialsException;
import com.devbraid.user.exception.RefreshTokenRevokedException;
import com.devbraid.user.exception.UserAlreadyExistsException;
import com.devbraid.user.exception.UserNotFoundException;
import com.devbraid.user.repository.RefreshTokenRepository;
import com.devbraid.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("UserService Unit Tests")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String FULL_NAME = "John Doe";
    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";
    private static final String HASHED_PASSWORD = "$2a$10$hashedPasswordForTesting";
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final String ROLE = "DEVELOPER";
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private OtpService otpService;
    @Captor
    private ArgumentCaptor<User> userCaptor;
    private UserService userService;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtTokenProvider, refreshTokenRepository, otpService);
        registerRequest = new RegisterRequest(FULL_NAME, EMAIL, PASSWORD);
    }

    @Test
    @DisplayName("register stores pending registration in Redis when email not yet verified")
    void register_StoresPendingInRedis_WhenEmailNotYetVerified() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(otpService.isEmailVerified(EMAIL)).thenReturn(false);

        userService.register(registerRequest);

        verify(otpService).storePendingRegistration(EMAIL, HASHED_PASSWORD, FULL_NAME);
        verify(otpService).isEmailVerified(EMAIL);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("register finalizes to PostgreSQL when OTP already verified (backward compat)")
    void register_FinalizesToPostgres_WhenOtpAlreadyVerified() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(otpService.isEmailVerified(EMAIL)).thenReturn(true);
        when(otpService.getPendingRegistration(EMAIL))
                .thenReturn(new OtpService.PendingUser(HASHED_PASSWORD, FULL_NAME));

        userService.register(registerRequest);

        verify(otpService).storePendingRegistration(EMAIL, HASHED_PASSWORD, FULL_NAME);
        verify(userRepository).save(userCaptor.capture());
        User captured = userCaptor.getValue();
        assertThat(captured.getFullName()).isEqualTo(FULL_NAME);
        assertThat(captured.getEmail()).isEqualTo(EMAIL);
        assertThat(captured.getPasswordHash()).isEqualTo(HASHED_PASSWORD);
        verify(otpService).deletePendingRegistration(EMAIL);
        verify(otpService).clearVerification(EMAIL);
    }

    @Test
    @DisplayName("register throws UserAlreadyExistsException when email is already registered")
    void register_ThrowsUserAlreadyExistsException() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Email already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("login returns LoginResponse with tokens when credentials are valid")
    void login_ReturnsLoginResponse() {
        User user = User.builder()
                .id(USER_ID)
                .fullName(FULL_NAME)
                .email(EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(USER_ID.toString(), EMAIL, ROLE)).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.createRefreshToken(USER_ID.toString(), EMAIL, ROLE)).thenReturn(REFRESH_TOKEN);
        when(jwtTokenProvider.getAccessExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtTokenProvider.getRefreshExpiresAt()).thenReturn(Instant.now().plusSeconds(604800));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = userService.login(EMAIL, PASSWORD);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getRole()).isEqualTo(ROLE);
        assertThat(response.getIssuedAt()).isNotNull();
        assertThat(response.getExpiresAt()).isNotNull();

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("login throws UserNotFoundException when email is not found")
    void login_ThrowsUserNotFoundException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found for email");
    }

    @Test
    @DisplayName("login throws InvalidCredentialsException when password is wrong")
    void login_ThrowsInvalidCredentialsException() {
        User user = User.builder()
                .id(USER_ID)
                .fullName(FULL_NAME)
                .email(EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASHED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("refreshToken returns new LoginResponse with valid token")
    void refreshToken_ReturnsNewTokens() {
        User user = User.builder()
                .id(USER_ID)
                .fullName(FULL_NAME)
                .email(EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .build();
        RefreshToken storedToken = RefreshToken.builder()
                .tokenHash("abc123")
                .user(user)
                .build();

        when(jwtTokenProvider.isRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(jwtTokenProvider.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID.toString());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(USER_ID.toString(), EMAIL, ROLE)).thenReturn("new-access");
        when(jwtTokenProvider.createRefreshToken(USER_ID.toString(), EMAIL, ROLE)).thenReturn("new-refresh");
        when(jwtTokenProvider.getAccessExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtTokenProvider.getRefreshExpiresAt()).thenReturn(Instant.now().plusSeconds(604800));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = userService.refreshToken(REFRESH_TOKEN);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenRepository).delete(storedToken);
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("refreshToken throws RefreshTokenRevokedException when token is revoked")
    void refreshToken_RevokedToken_ThrowsException() {
        User user = User.builder().id(USER_ID).fullName(FULL_NAME).email(EMAIL).build();
        RefreshToken revokedToken = RefreshToken.builder().tokenHash("abc123").user(user).build();
        revokedToken.revoke();

        when(jwtTokenProvider.isRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> userService.refreshToken(REFRESH_TOKEN))
                .isInstanceOf(RefreshTokenRevokedException.class)
                .hasMessage("Refresh token has been revoked");
    }

    @Test
    @DisplayName("logout deletes the refresh token")
    void logout_RevokesToken() {
        User user = User.builder().id(USER_ID).fullName(FULL_NAME).email(EMAIL).build();
        RefreshToken storedToken = RefreshToken.builder().tokenHash("abc123").user(user).build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        userService.logout(REFRESH_TOKEN);

        verify(refreshTokenRepository).delete(storedToken);
    }

    @Test
    @DisplayName("logout throws InvalidCredentialsException when token not found")
    void logout_NotFound_ThrowsException() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.logout(REFRESH_TOKEN))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
