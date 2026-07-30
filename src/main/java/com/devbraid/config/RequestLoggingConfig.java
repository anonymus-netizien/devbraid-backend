package com.devbraid.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replaces the hand-rolled LoggingFilter with Spring's built-in CommonsRequestLoggingFilter.
 * Masks sensitive headers before logging.
 * <p>
 * Log level: org.springframework.web.filter.CommonsRequestLoggingFilter=DEBUG in application.yml.
 */
@Slf4j
@Configuration
public class RequestLoggingConfig {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "x-api-key", "set-cookie"
    );

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter() {
            @Override
            protected void beforeRequest(HttpServletRequest request, String message) {
                log.info("Incoming Request: {} {} {}",
                        request.getMethod(),
                        request.getRequestURI(),
                        sanitizedHeaders(request));
            }
        };
        filter.setIncludeQueryString(true);
        filter.setIncludeClientInfo(true);
        filter.setMaxPayloadLength(10000);
        return filter;
    }

    /**
     * Builds a sanitized header string with sensitive values masked.
     */
    private static String sanitizedHeaders(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames()).stream()
                .map(name -> {
                    String value = SENSITIVE_HEADERS.contains(name.toLowerCase())
                            ? "[REDACTED]"
                            : String.join(",", Collections.list(request.getHeaders(name)));
                    return name + ": " + value;
                })
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
