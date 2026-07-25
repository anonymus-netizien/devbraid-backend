package com.devbraid.user;

import com.devbraid.security.JwtTokenProvider;
import com.devbraid.user.dto.LoginResponse;
import com.devbraid.user.dto.RegisterRequest;
import com.devbraid.user.dto.UserProfileResponse;
import com.devbraid.user.exception.InvalidCredentialsException;
import com.devbraid.user.exception.RefreshTokenRevokedException;
import com.devbraid.user.exception.UserAlreadyExistsException;
import com.devbraid.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public void register(RegisterRequest request) {
        log.info("UserService :: Register request for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);

        log.info("UserService :: User registered for email: {}", request.getEmail());
    }

    public LoginResponse login(String email, String password) {
        log.info("UserService :: Login request for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found for email: " + email));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        LoginResponse response = buildLoginResponse(user);
        persistRefreshToken(response.getRefreshToken(), user);
        return response;
    }

    public UserProfileResponse getUserProfile(String userId) {
        log.info("UserService :: Get user profile for id: {}", userId);

        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role("DEVELOPER")
                .createdAt(user.getCreatedAt())
                .build();
    }

    public LoginResponse refreshToken(String refreshToken) {
        log.info("UserService :: Refresh token request");

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }

        // Look up and validate against DB
        String tokenHash = hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token not found"));

        if (storedToken.isRevoked()) {
            log.warn("UserService :: Attempted use of revoked refresh token");
            throw new RefreshTokenRevokedException("Refresh token has been revoked");
        }

        // Revoke old token (rotation)
        storedToken.revoke();
        refreshTokenRepository.save(storedToken);

        // Issue new tokens
        String userId = jwtTokenProvider.getUserId(refreshToken);
        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        LoginResponse response = buildLoginResponse(user);
        persistRefreshToken(response.getRefreshToken(), user);

        log.info("UserService :: Token refreshed for user id: {}", userId);
        return response;
    }

    public void logout(String refreshToken) {
        log.info("UserService :: Logout request");

        String tokenHash = hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        storedToken.revoke();
        refreshTokenRepository.save(storedToken);

        log.info("UserService :: Refresh token revoked for user");
    }

    private LoginResponse buildLoginResponse(User user) {
        String userId = user.getId().toString();
        String userRole = "DEVELOPER";

        String accessToken = jwtTokenProvider.createAccessToken(userId, user.getEmail(), userRole);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, user.getEmail(), userRole);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .issuedAt(Instant.now())
                .expiresAt(jwtTokenProvider.getAccessExpiresAt())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .userId(user.getId())
                .role(userRole)
                .build();
    }

    private void persistRefreshToken(String rawToken, User user) {
        RefreshToken tokenEntity = RefreshToken.builder()
                .tokenHash(hashToken(rawToken))
                .user(user)
                .expiresAt(OffsetDateTime.ofInstant(jwtTokenProvider.getRefreshExpiresAt(), ZoneOffset.UTC))
                .build();
        refreshTokenRepository.save(tokenEntity);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
