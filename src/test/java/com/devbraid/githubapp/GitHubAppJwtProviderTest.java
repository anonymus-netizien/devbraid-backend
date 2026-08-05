package com.devbraid.githubapp;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for GitHubAppJwtProvider JWT signing.
 */
@DisplayName("GitHubAppJwtProvider Unit Tests")
class GitHubAppJwtProviderTest {

    private static final long APP_ID = 123456L;

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static byte[] pkcs1Der(KeyPair keyPair) {
        byte[] pkcs8 = keyPair.getPrivate().getEncoded();
        byte[] pkcs1 = new byte[pkcs8.length - 26];
        System.arraycopy(pkcs8, 26, pkcs1, 0, pkcs1.length);
        return pkcs1;
    }

    private static String pem(String header, byte[] der) {
        return header + "\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der)
                + "\n" + header.replace("BEGIN", "END");
    }

    @Test
    @DisplayName("createAppJwt() signs a valid RS256 JWT from a PKCS#8 key")
    void createAppJwt_Pkcs8Key_ReturnsVerifiedRs256Jwt() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String pem = pem("-----BEGIN PRIVATE KEY-----", keyPair.getPrivate().getEncoded());

        String jwt = new GitHubAppJwtProvider(APP_ID, pem).createAppJwt();

        assertJwt(jwt, keyPair);
    }

    @Test
    @DisplayName("createAppJwt() signs a valid RS256 JWT from a PKCS#1 key")
    void createAppJwt_Pkcs1Key_ReturnsVerifiedRs256Jwt() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String pem = pem("-----BEGIN RSA PRIVATE KEY-----", pkcs1Der(keyPair));

        String jwt = new GitHubAppJwtProvider(APP_ID, pem).createAppJwt();

        assertJwt(jwt, keyPair);
    }

    @Test
    @DisplayName("createAppJwt() throws IllegalStateException without an app id")
    void createAppJwt_NoAppId_ThrowsIllegalStateException() throws Exception {
        KeyPair keyPair = generateKeyPair();
        GitHubAppJwtProvider provider = new GitHubAppJwtProvider(null,
                pem("-----BEGIN PRIVATE KEY-----", keyPair.getPrivate().getEncoded()));

        assertThatThrownBy(provider::createAppJwt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GitHub App credentials not configured");
    }

    @Test
    @DisplayName("createAppJwt() throws IllegalStateException without a private key")
    void createAppJwt_BlankPrivateKey_ThrowsIllegalStateException() {
        GitHubAppJwtProvider provider = new GitHubAppJwtProvider(APP_ID, " ");

        assertThatThrownBy(provider::createAppJwt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GitHub App credentials not configured");
    }

    private void assertJwt(String jwt, KeyPair keyPair) {
        DecodedJWT decoded = JWT.decode(jwt);
        assertThat(decoded.getAlgorithm()).isEqualTo("RS256");
        assertThat(decoded.getIssuer()).isEqualTo(String.valueOf(APP_ID));
        assertThat(decoded.getExpiresAtAsInstant().getEpochSecond() - decoded.getIssuedAtAsInstant().getEpochSecond())
                .isEqualTo(540);
        assertThatNoException().isThrownBy(() -> JWT.require(
                        Algorithm.RSA256((RSAPublicKey) keyPair.getPublic(), null))
                .build().verify(jwt));
    }
}
