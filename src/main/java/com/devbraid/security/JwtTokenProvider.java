package com.devbraid.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final Algorithm algorithm;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration:604800000}") long refreshExpirationMs) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String createAccessToken(String userId, String email, String role) {
        return JWT.create()
                .withSubject(userId)
                .withClaim("email", email)
                .withClaim("role", role)
                .withClaim("type", "access")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + accessExpirationMs))
                .sign(algorithm);
    }

    public String createRefreshToken(String userId, String email, String role) {
        return JWT.create()
                .withSubject(userId)
                .withClaim("email", email)
                .withClaim("role", role)
                .withClaim("type", "refresh")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .sign(algorithm);
    }

    public Instant getAccessExpiresAt() {
        return Instant.now().plusMillis(accessExpirationMs);
    }

    public Instant getRefreshExpiresAt() {
        return Instant.now().plusMillis(refreshExpirationMs);
    }

    public boolean isRefreshToken(String token) {
        try {
            DecodedJWT decoded = verify(token);
            return "refresh".equals(decoded.getClaim("type").asString());
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    public DecodedJWT verify(String token) throws JWTVerificationException {
        return JWT.require(algorithm).build().verify(token);
    }

    public String getUserId(String token) {
        return verify(token).getSubject();
    }

    public String getEmail(String token) {
        return verify(token).getClaim("email").asString();
    }

    public String getRole(String token) {
        return verify(token).getClaim("role").asString();
    }

    public Instant getIssuedAt(String token) {
        return verify(token).getIssuedAtAsInstant();
    }

    public Instant getExpiresAt(String token) {
        return verify(token).getExpiresAtAsInstant();
    }
}
