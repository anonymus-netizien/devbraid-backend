package com.devbraid.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac";
    private static final long ACCESS_EXPIRATION_MS = 3600000; // 1 hour
    private static final long REFRESH_EXPIRATION_MS = 604800000; // 7 days
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String EMAIL = "test@example.com";
    private static final String ROLE = "DEVELOPER";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
    }

    @Test
    @DisplayName("createAccessToken returns a valid JWT")
    void createAccessToken_ReturnsValidToken() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);

        assertThat(token).isNotNull().isNotEmpty();
        assertThatNoException().isThrownBy(() -> jwtTokenProvider.verify(token));
    }

    @Test
    @DisplayName("createRefreshToken returns a valid JWT with longer expiry")
    void createRefreshToken_ReturnsValidToken() {
        String token = jwtTokenProvider.createRefreshToken(USER_ID, EMAIL, ROLE);
        DecodedJWT decoded = jwtTokenProvider.verify(token);

        assertThat(token).isNotNull().isNotEmpty();
        assertThat(decoded.getClaim("type").asString()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("consecutive refresh tokens are unique (jti claim)")
    void createRefreshToken_ConsecutiveTokens_AreUnique() {
        String token1 = jwtTokenProvider.createRefreshToken(USER_ID, EMAIL, ROLE);
        String token2 = jwtTokenProvider.createRefreshToken(USER_ID, EMAIL, ROLE);

        assertThat(token1).isNotEqualTo(token2);
        assertThat(jwtTokenProvider.verify(token1).getId()).isNotEqualTo(jwtTokenProvider.verify(token2).getId());
    }

    @Test
    @DisplayName("verify with valid token succeeds")
    void verify_ValidToken_Succeeds() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);

        DecodedJWT decoded = jwtTokenProvider.verify(token);

        assertThat(decoded).isNotNull();
    }

    @Test
    @DisplayName("verify with invalid token throws JWTVerificationException")
    void verify_InvalidToken_ThrowsException() {
        String invalidToken = "invalid.jwt.token";

        assertThatThrownBy(() -> jwtTokenProvider.verify(invalidToken))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    @DisplayName("verify with tampered token throws JWTVerificationException")
    void verify_TamperedToken_ThrowsException() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);
        String tamperedToken = token.substring(0, token.lastIndexOf('.')) + ".tampered";

        assertThatThrownBy(() -> jwtTokenProvider.verify(tamperedToken))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    @DisplayName("getUserId returns correct subject from token")
    void getUserId_ReturnsCorrectSubject() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);

        String userId = jwtTokenProvider.getUserId(token);

        assertThat(userId).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("getEmail returns correct email claim from token")
    void getEmail_ReturnsCorrectClaim() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);

        String email = jwtTokenProvider.getEmail(token);

        assertThat(email).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("getRole returns correct role claim from token")
    void getRole_ReturnsCorrectClaim() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);

        String role = jwtTokenProvider.getRole(token);

        assertThat(role).isEqualTo(ROLE);
    }

    @Test
    @DisplayName("access token has type claim set to 'access'")
    void accessToken_HasTypeClaimAccess() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);
        DecodedJWT decoded = jwtTokenProvider.verify(token);

        String type = decoded.getClaim("type").asString();

        assertThat(type).isEqualTo("access");
    }

    @Test
    @DisplayName("refresh token has type claim set to 'refresh'")
    void refreshToken_HasTypeClaimRefresh() {
        String token = jwtTokenProvider.createRefreshToken(USER_ID, EMAIL, ROLE);
        DecodedJWT decoded = jwtTokenProvider.verify(token);

        String type = decoded.getClaim("type").asString();

        assertThat(type).isEqualTo("refresh");
    }

    @Test
    @DisplayName("access token expires within configured duration")
    void accessToken_ExpiresWithinConfiguredTime() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);
        DecodedJWT decoded = jwtTokenProvider.verify(token);

        Instant issuedAt = decoded.getIssuedAtAsInstant();
        Instant expiresAt = decoded.getExpiresAtAsInstant();

        assertThat(expiresAt).isAfter(issuedAt);
        assertThat(java.time.Duration.between(issuedAt, expiresAt).toMillis())
                .isCloseTo(ACCESS_EXPIRATION_MS, byLessThan(2000L)); // allow 2s tolerance
    }

    @Test
    @DisplayName("getAccessExpiresAt returns time in the future")
    void getAccessExpiresAt_ReturnsFutureTime() {
        Instant expiresAt = jwtTokenProvider.getAccessExpiresAt();

        assertThat(expiresAt).isAfter(Instant.now());
    }
}
