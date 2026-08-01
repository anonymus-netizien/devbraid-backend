package com.devbraid.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * JDK crypto utilities — wraps checked exceptions once at the boundary.
 * Services use these methods without try-catches; RuntimeException propagates
 * to GlobalExceptionHandler (400/500) as required.
 * <p>
 * ponytail: MessageDigest and Mac are NOT thread-safe — create per-call.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * SHA-256 hash of a string, returned as lowercase hex.
     */
    public static String sha256Hex(String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256 hash of a byte array, returned as lowercase hex.
     */
    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Create a configured HMAC instance for the given secret and algorithm.
     * Centralizes the GeneralSecurityException boundary so callers stay try-catch-free.
     */
    public static Mac createHmac(String secret, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return mac;
        } catch (java.security.GeneralSecurityException e) {
            throw new RuntimeException(algorithm + " initialization failed", e);
        }
    }
}
