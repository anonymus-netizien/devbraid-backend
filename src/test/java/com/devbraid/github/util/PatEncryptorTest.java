package com.devbraid.github.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PatEncryptor Unit Tests")
class PatEncryptorTest {

    private static final String VALID_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String DIFFERENT_KEY = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
    private static final String PAT = "ghp_testPersonalAccessToken1234567890abcdef";
    private PatEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new PatEncryptor(VALID_KEY);
    }

    @Test
    @DisplayName("encrypt and decrypt roundtrip returns original PAT")
    void encryptDecrypt_Roundtrip_ReturnsOriginal() {
        byte[] iv = encryptor.generateIv();
        byte[] encrypted = encryptor.encrypt(PAT, iv);
        String decrypted = encryptor.decrypt(encrypted, iv);

        assertThat(decrypted).isEqualTo(PAT);
    }

    @Test
    @DisplayName("encrypt produces different ciphertext for same PAT with different IV")
    void encrypt_SamePatDifferentIv_ProducesDifferentCiphertext() {
        byte[] iv1 = encryptor.generateIv();
        byte[] iv2 = encryptor.generateIv();
        byte[] encrypted1 = encryptor.encrypt(PAT, iv1);
        byte[] encrypted2 = encryptor.encrypt(PAT, iv2);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("decrypt with wrong key throws RuntimeException")
    void decrypt_WrongKey_ThrowsException() {
        PatEncryptor wrongEncryptor = new PatEncryptor(DIFFERENT_KEY);
        byte[] iv = encryptor.generateIv();
        byte[] encrypted = encryptor.encrypt(PAT, iv);

        assertThatThrownBy(() -> wrongEncryptor.decrypt(encrypted, iv))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    @DisplayName("decrypt with tampered ciphertext throws RuntimeException")
    void decrypt_TamperedCiphertext_ThrowsException() {
        byte[] iv = encryptor.generateIv();
        byte[] encrypted = encryptor.encrypt(PAT, iv);
        // Tamper with the ciphertext
        encrypted[2] = (byte) (encrypted[2] ^ 0xff);

        assertThatThrownBy(() -> encryptor.decrypt(encrypted, iv))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    @DisplayName("decrypt with wrong IV throws RuntimeException")
    void decrypt_WrongIv_ThrowsException() {
        byte[] iv = encryptor.generateIv();
        byte[] wrongIv = encryptor.generateIv();
        byte[] encrypted = encryptor.encrypt(PAT, iv);

        assertThatThrownBy(() -> encryptor.decrypt(encrypted, wrongIv))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    @DisplayName("generateIv produces 12-byte IV (GCM standard)")
    void generateIv_Produces12Bytes() {
        byte[] iv = encryptor.generateIv();
        assertThat(iv).hasSize(12);
    }

    @Test
    @DisplayName("encrypt handles empty PAT")
    void encrypt_EmptyPat_Succeeds() {
        byte[] iv = encryptor.generateIv();
        byte[] encrypted = encryptor.encrypt("", iv);
        String decrypted = encryptor.decrypt(encrypted, iv);

        assertThat(decrypted).isEmpty();
    }

    @Test
    @DisplayName("encrypt handles very long PAT")
    void encrypt_LongPat_Succeeds() {
        String longPat = "ghp_" + "a".repeat(100);
        byte[] iv = encryptor.generateIv();
        byte[] encrypted = encryptor.encrypt(longPat, iv);
        String decrypted = encryptor.decrypt(encrypted, iv);

        assertThat(decrypted).isEqualTo(longPat);
    }
}
