package com.devbraid.github.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class PatEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12; // bytes, GCM standard

    private final SecretKey secretKey;

    public PatEncryptor(@Value("${app.encryption.key}") String hexKey) {
        byte[] keyBytes = HexFormat.of().parseHex(hexKey);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Generate a new random IV for each encryption.
     */
    public byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * Encrypt a PAT with AES-256-GCM. Returns ciphertext.
     * IV is managed separately by the caller.
     */
    public byte[] encrypt(String pat, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(pat.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt PAT", e);
        }
    }

    /**
     * Decrypt a PAT. Requires the same IV that was used during encryption.
     */
    public String decrypt(byte[] encryptedPat, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(encryptedPat);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt PAT", e);
        }
    }
}
