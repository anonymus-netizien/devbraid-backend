package com.devbraid.apikey.security;

import com.devbraid.apikey.entity.ApiKey;
import com.devbraid.apikey.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates machine calls via X-API-Key header and enforces the key's
 * per-minute sliding-window limit (429 + Retry-After on breach).
 * No try/catch here — validation failures simply leave the context unauthenticated,
 * and the SecurityConfig's authenticated() rule returns 401.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            ApiKey apiKey = apiKeyService.validate(apiKeyHeader);
            if (apiKey == null) {
                log.warn("ApiKey :: invalid or inactive key for {}", request.getRequestURI());
            } else if (!apiKeyService.isAllowed(apiKey)) {
                log.warn("ApiKey :: rate limited for {} on {}", apiKey.getPrefix(), request.getRequestURI());
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(apiKeyService.retryAfterSeconds(apiKey)));
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Rate limit exceeded\"}");
                return;
            } else {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_API"));
                var auth = new UsernamePasswordAuthenticationToken(apiKey, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
