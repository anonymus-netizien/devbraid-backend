package com.devbraid.common;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter implements Filter {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "x-api-key", "set-cookie"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;

        log.info("""
                        
                        ==========================================
                        Incoming Request
                        ==========================================
                        Method       : {}
                        URI          : {}
                        Headers      : {}
                        Host         : {}
                        Query Params : {}
                        Timestamp    : {}
                        ==========================================
                        """,
                request.getMethod(),
                request.getRequestURI(),
                java.util.Collections.list(request.getHeaderNames()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                h -> h,
                                h -> SENSITIVE_HEADERS.contains(h.toLowerCase())
                                        ? "[REDACTED]"
                                        : String.join(",", java.util.Collections.list(request.getHeaders(h))))),
                request.getHeader("Host"),
                request.getQueryString() != null ? request.getQueryString() : "(none)",
                LocalDateTime.now()
        );

        chain.doFilter(servletRequest, servletResponse);
    }
}
