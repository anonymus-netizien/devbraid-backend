package com.devbraid.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authenticates STOMP CONNECT frames via Authorization: Bearer <jwt>.
 * Rejects the handshake when the token is missing or invalid (no anonymous frames).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompJwtAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Stomp :: CONNECT rejected — missing Authorization header");
                throw new IllegalArgumentException("Authentication required");
            }
            try {
                DecodedJWT jwt = jwtTokenProvider.verify(authHeader.substring(7));
                String userId = jwt.getSubject();
                String role = jwt.getClaim("role").asString();
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                accessor.setUser(auth);
                log.debug("Stomp :: CONNECT authenticated for user {}", userId);
            } catch (JWTVerificationException e) {
                log.warn("Stomp :: CONNECT rejected — invalid token: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid or expired token");
            }
        }
        return message;
    }
}
