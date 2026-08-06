package com.devbraid.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.devbraid.user.entity.User;
import com.devbraid.user.service.UserService;
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
 * Authenticates requests with a Clerk session token (Bearer JWT).
 * The Clerk user is mirrored into the local users table on first request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClerkJwtFilter extends OncePerRequestFilter {

    private final ClerkJwtVerifier clerkJwtVerifier;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                DecodedJWT jwt = clerkJwtVerifier.verify(token);
                String clerkId = jwt.getSubject();
                String email = jwt.getClaim("email").asString();
                String fullName = fullNameOf(jwt);

                if (clerkId == null || email == null) {
                    log.warn("ClerkJwtFilter :: token missing sub or email claim");
                    SecurityContextHolder.clearContext();
                } else {
                    User user = userService.syncClerkUser(clerkId, email, fullName);
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER"));
                    var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("ClerkJwtFilter :: Authenticated Clerk user: {}", email);
                }
            } catch (Exception e) {
                log.warn("ClerkJwtFilter :: Invalid Clerk token: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String fullNameOf(DecodedJWT jwt) {
        String fullName = jwt.getClaim("fullName").asString();
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String first = jwt.getClaim("first_name").asString();
        String last = jwt.getClaim("last_name").asString();
        if (first == null && last == null) {
            return null;
        }
        return (first == null ? "" : first) + " " + (last == null ? "" : last);
    }
}
