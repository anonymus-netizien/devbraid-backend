package com.devbraid.githubapp;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Signs a short-lived RS256 JWT for GitHub App authentication.
 */
@Slf4j
@Component
public class GitHubAppJwtProvider {

    private static final int JWT_EXPIRATION_SECONDS = 540;

    private final Long appId;
    private final String privateKey;

    public GitHubAppJwtProvider(
            @Value("${github.app.app-id:}") Long appId,
            @Value("${github.app.private-key:}") String privateKey) {
        this.appId = appId;
        this.privateKey = privateKey;
    }

    public String createAppJwt() {
        if (appId == null || privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException("GitHub App credentials not configured");
        }
        byte[] der = Base64.getDecoder().decode(stripPemHeaders(privateKey));
        if (privateKey.contains("BEGIN RSA PRIVATE KEY")) {
            der = toPkcs8(der);
        }
        Algorithm algorithm = Algorithm.RSA256(null, loadPrivateKey(der));
        long now = Instant.now().getEpochSecond();
        return JWT.create()
                .withIssuer(String.valueOf(appId))
                .withIssuedAt(Date.from(Instant.ofEpochSecond(now)))
                .withExpiresAt(Date.from(Instant.ofEpochSecond(now + JWT_EXPIRATION_SECONDS)))
                .sign(algorithm);
    }

    private String stripPemHeaders(String pem) {
        return pem.replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }

    private byte[] toPkcs8(byte[] pkcs1) {
        byte[] pkcs8 = new byte[26 + pkcs1.length];
        pkcs8[0] = 0x30;
        pkcs8[1] = (byte) 0x82;
        pkcs8[2] = (byte) ((pkcs1.length + 22) >> 8);
        pkcs8[3] = (byte) (pkcs1.length + 22);
        pkcs8[4] = 0x02;
        pkcs8[5] = 0x01;
        pkcs8[6] = 0x00;
        byte[] algorithmId = {
                0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7,
                0x0D, 0x01, 0x01, 0x01, 0x05, 0x00
        };
        System.arraycopy(algorithmId, 0, pkcs8, 7, algorithmId.length);
        pkcs8[22] = 0x04;
        pkcs8[23] = (byte) 0x82;
        pkcs8[24] = (byte) (pkcs1.length >> 8);
        pkcs8[25] = (byte) pkcs1.length;
        System.arraycopy(pkcs1, 0, pkcs8, 26, pkcs1.length);
        return pkcs8;
    }

    private RSAPrivateKey loadPrivateKey(byte[] der) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse GitHub App private key", e);
        }
    }
}
