package com.devbraid.user;

import com.devbraid.security.JwtTokenProvider;
import com.devbraid.user.dto.LoginResponse;
import com.devbraid.user.dto.RegisterRequest;
import com.devbraid.user.exception.InvalidCredentialsException;
import com.devbraid.user.exception.RefreshTokenRevokedException;
import com.devbraid.user.exception.UserAlreadyExistsException;
import com.devbraid.user.exception.UserNotFoundException;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("UserService Unit Tests")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private UserService userService;

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String FULL_NAME = "John Doe";
    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";
    private static final String HASHED_PASSWORD = "$2a$10$hashedPasswordForTesting";
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final String ROLE = "DEVELOPER";

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtTokenProvider, refreshTokenRepository);
        registerRequest = new RegisterRequest(FULL_NAME, EMAIL, PASSWORD);
    }

    // --- Register Tests ---

    @Test
    @DisplayName("register creates user when email is not already registered")
    void register_CreatesUserSuccessfully() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            User savedUser = User.builder()
                    .id(USER_ID)
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .passwordHash(user.getPasswordHash())
                    .build();
            return savedUser;
        });

        userService.register(registerRequest);

        verify(userRepository).existsByEmail(EMAIL);
        verify(passwordEncoder).encode(PASSWORD);
        verify(userRepository).save(userCaptor.capture());
        User captured = userCaptor.getValue();
        assertThat(captured.getFullName()).isEqualTo(FULL_NAME);
        assertThat(captured.getEmail()).isEqualTo(EMAIL);
        assertThat(captured.getPasswordHash()).isEqualTo(HASHED_PASSWORD);
    }

    @Test
    @DisplayName("register throws UserAlreadyExistsException when email is already registered")
    void register_ThrowsUserAlreadyExistsException() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Email already registered");

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("register throws UserAlreadyExistsException with correct message")
    void register_ThrowsUserAlreadyExistsException_WithCorrectMessage() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email already registered");
    }

    // --- Login Tests ---

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
        assertThat(response.getExpiresAt()).isAfter(response.getIssuedAt());

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("login throws UserNotFoundException when email is not found")
    void login_ThrowsUserNotFoundException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found for email");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).createAccessToken(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("login throws UserNotFoundException with email in message")
    void login_ThrowsUserNotFoundException_WithEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(EMAIL);
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

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("login with empty password does not throw unexpected exceptions")
    void login_WithEmptyPassword() {
        User user = User.builder()
                .id(USER_ID)
                .fullName(FULL_NAME)
                .email(EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("", HASHED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> userService.login(EMAIL, ""))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // --- Refresh Token Tests ---

    @Test
    @DisplayName("refreshToken returns new LoginResponse with valid token")
    void refreshToken_ReturnsNewTokens() {
        String tokenHash = "abc123";
        User user = User.builder()
                .id(USER_ID)
                .fullName(FULL_NAME)
                .email(EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .build();
        RefreshToken storedToken = RefreshToken.builder()
                .tokenHash(tokenHash)
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
        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("refreshToken throws RefreshTokenRevokedException when token is revoked")
    void refreshToken_RevokedToken_ThrowsException() {
        User user = User.builder()
                .id(USER_ID)
                .fullName(FULL_NAME)
                .email(EMAIL)
                .build();
        RefreshToken revokedToken = RefreshToken.builder()
                .tokenHash("abc123")
                .user(user)
                .build();
        revokedToken.revoke();

        when(jwtTokenProvider.isRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> userService.refreshToken(REFRESH_TOKEN))
                .isInstanceOf(RefreshTokenRevokedException.class)
                .hasMessage("Refresh token has been revoked");
    }

    @Test
    @DisplayName("refreshToken throws InvalidCredentialsException when token not in DB")
    void refreshToken_NotFound_ThrowsException() {
        when(jwtTokenProvider.isRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.refreshToken(REFRESH_TOKEN))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token not found");
    }

    // --- Logout Tests ---

    @Test
    @DisplayName("logout revokes the refresh token")
    void logout_RevokesToken() {
        User user = User.builder()
                .id(USER_ID)
                .fullName(FULL_NAME)
                .email(EMAIL)
                .build();
        RefreshToken storedToken = RefreshToken.builder()
                .tokenHash("abc123")
                .user(user)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.logout(REFRESH_TOKEN);

        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedToken);
    }

    @Test
    @DisplayName("logout throws InvalidCredentialsException when token not found")
    void logout_NotFound_ThrowsException() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.logout(REFRESH_TOKEN))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
