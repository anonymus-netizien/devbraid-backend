package com.devbraid.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("StompJwtAuthInterceptor Unit Tests")
class StompJwtAuthInterceptorTest {

    private static final String SECRET = "test-secret-for-unit-tests";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    private JwtTokenProvider tokenProvider;
    private StompJwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 900000, 604800000);
        interceptor = new StompJwtAuthInterceptor(tokenProvider);
    }

    private String validToken() {
        return JWT.create()
                .withSubject(USER_ID)
                .withClaim("email", "user@example.com")
                .withClaim("role", "DEVELOPER")
                .withClaim("type", "access")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 900000))
                .sign(Algorithm.HMAC256(SECRET));
    }

    /**
     * Create a CONNECT frame with mutable headers.
     * ponytail: MessageBuilder.createMessage() freezes headers into immutable MessageHeaders,
     * preventing the interceptor from calling setUser(). We build a GenericMessage with a
     * mutable MessageHeaderAccessor so the interceptor can modify it in place.
     */
    private Message<?> connectFrame(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("CONNECT with valid Bearer token sets authenticated user")
    void connect_withValidToken_setsUser() {
        // Build a mutable CONNECT frame so the interceptor can call setUser()
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer " + validToken());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));

        // Retrieve the accessor from the result — it should now carry the authenticated user
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser(), "user must be set on authenticated CONNECT");
        assertEquals(USER_ID, resultAccessor.getUser().getName());
    }

    @Test
    @DisplayName("CONNECT without Authorization header is rejected")
    void connect_withoutAuthHeader_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(connectFrame(null), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    @DisplayName("CONNECT with invalid token is rejected")
    void connect_withInvalidToken_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(connectFrame("Bearer not-a-real-token"), mock(org.springframework.messaging.MessageChannel.class)));
    }
}
