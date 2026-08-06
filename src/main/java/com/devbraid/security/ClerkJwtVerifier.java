package com.devbraid.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies Clerk session tokens (RS256 JWTs) against Clerk's published JWKS.
 * Keys are cached for an hour and re-fetched when an unknown kid appears
 * (Clerk rotates keys; the cache self-heals).
 */
@Slf4j
@Component
public class ClerkJwtVerifier {

    private static final long CACHE_TTL_SECONDS = 3600;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String jwksUrl;
    private final String issuer;
    private final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();
    private Instant lastFetch = Instant.EPOCH;

    public ClerkJwtVerifier(RestClient.Builder restClientBuilder,
                            ObjectMapper objectMapper,
                            @Value("${app.clerk.jwks-url}") String jwksUrl,
                            @Value("${app.clerk.issuer:}") String issuer) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.jwksUrl = jwksUrl;
        this.issuer = issuer;
    }

    public DecodedJWT verify(String token) {
        var verifier = JWT.require(Algorithm.RSA256(keyProvider()));
        if (issuer != null && !issuer.isBlank()) {
            verifier.withIssuer(issuer);
        }
        return verifier.build().verify(token);
    }

    private RSAKeyProvider keyProvider() {
        return new RSAKeyProvider() {
            @Override
            public RSAPublicKey getPublicKeyById(String kid) {
                return getKey(kid);
            }

            @Override
            public RSAPrivateKey getPrivateKey() {
                return null;
            }

            @Override
            public String getPrivateKeyId() {
                return null;
            }
        };
    }

    private synchronized RSAPublicKey getKey(String kid) {
        RSAPublicKey cached = keyCache.get(kid);
        if (cached != null && Instant.now().isBefore(lastFetch.plusSeconds(CACHE_TTL_SECONDS))) {
            return cached;
        }
        refreshKeys();
        return keyCache.get(kid);
    }

    private void refreshKeys() {
        keyCache.clear();
        lastFetch = Instant.now();
        try {
            JsonNode jwks = objectMapper.readTree(restClient.get().uri(jwksUrl).retrieve().body(String.class));
            for (JsonNode key : jwks.path("keys")) {
                if (!"RSA".equals(key.path("kty").asText())) {
                    continue;
                }
                String kid = key.path("kid").asText();
                BigInteger n = base64UrlToBigInteger(key.path("n").asText());
                BigInteger e = base64UrlToBigInteger(key.path("e").asText());
                RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(n, e));
                keyCache.put(kid, publicKey);
            }
            log.info("ClerkJwtVerifier :: loaded {} JWKS keys", keyCache.size());
        } catch (Exception ex) {
            log.warn("ClerkJwtVerifier :: JWKS fetch failed: {}", ex.getMessage());
        }
    }

    private static BigInteger base64UrlToBigInteger(String value) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(value));
    }
}
