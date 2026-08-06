package com.devbraid.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ClerkJwtVerifier")
class ClerkJwtVerifierTest {

    private static WireMockServer wireMock;
    private static KeyPair keyPair;
    private static String jwksUrl;
    private static final String ISS = "https://test-app.clerk.accounts";

    @BeforeAll
    static void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        RSAPublicKey pub = (RSAPublicKey) keyPair.getPublic();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode key = mapper.createObjectNode();
        key.put("kty", "RSA");
        key.put("kid", "test-key-1");
        key.put("alg", "RS256");
        key.put("use", "sig");
        key.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray()));
        key.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray()));
        ObjectNode jwks = mapper.createObjectNode();
        ArrayNode keys = jwks.putArray("keys");
        keys.add(key);

        jwksUrl = wireMock.baseUrl() + "/jwks";
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/jwks"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(jwks))));
    }

    @AfterAll
    static void tearDown() {
        wireMock.stop();
    }

    private ClerkJwtVerifier verifier() {
        return new ClerkJwtVerifier(RestClient.builder(), new ObjectMapper(), jwksUrl, ISS);
    }

    private String signedToken(String subject, String email) {
        return JWT.create()
                .withSubject(subject)
                .withClaim("email", email)
                .withIssuer(ISS)
                .withKeyId("test-key-1")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.RSA256((RSAPublicKey) keyPair.getPublic(), (java.security.interfaces.RSAPrivateKey) keyPair.getPrivate()));
    }

    @Test
    @DisplayName("verifies a valid Clerk session token signed by the JWKS key")
    void verify_ValidToken_Succeeds() {
        var jwt = verifier().verify(signedToken("user_2abc123", "john@example.com"));

        assertThat(jwt.getSubject()).isEqualTo("user_2abc123");
        assertThat(jwt.getClaim("email").asString()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("rejects a token signed by a different key")
    void verify_ForeignKey_Throws() throws Exception {
        KeyPair other = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String token = JWT.create()
                .withSubject("user_2abc123")
                .withIssuer(ISS)
                .withKeyId("test-key-1")
                .sign(Algorithm.RSA256((RSAPublicKey) other.getPublic(), (java.security.interfaces.RSAPrivateKey) other.getPrivate()));

        assertThatThrownBy(() -> verifier().verify(token))
                .hasMessageContaining("Signature resulted invalid");
    }

    @Test
    @DisplayName("rejects a token with an unknown key id")
    void verify_UnknownKid_Throws() {
        String token = JWT.create()
                .withSubject("user_2abc123")
                .withIssuer(ISS)
                .withKeyId("unknown-kid")
                .sign(Algorithm.RSA256((RSAPublicKey) keyPair.getPublic(), (java.security.interfaces.RSAPrivateKey) keyPair.getPrivate()));

        assertThatThrownBy(() -> verifier().verify(token));
    }
}
