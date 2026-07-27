package com.devbraid.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.devbraid.user.entity.User;
import com.devbraid.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("JwtAuthenticationFilter Unit Tests")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String EMAIL = "test@example.com";
    private static final String ROLE = "DEVELOPER";
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private UserRepository userRepository;
    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3600000, 604800000);
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
    }

    @Test
    @DisplayName("valid token sets authentication in SecurityContext")
    void validToken_SetsAuthentication() throws Exception {
        User mockUser = User.builder()
                .id(UUID.fromString(USER_ID))
                .email(EMAIL)
                .fullName("Test User")
                .build();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockUser));

        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(mockUser);
        assertThat(auth.getAuthorities())
                .hasSize(1);
        assertThat(auth.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_DEVELOPER");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("no Authorization header passes through without authentication")
    void noAuthHeader_PassesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("empty Authorization header passes through without authentication")
    void emptyAuthHeader_PassesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("non-Bearer Authorization header passes through without authentication")
    void nonBearerAuthHeader_PassesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dGVzdDpwYXNz");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("invalid token clears any existing authentication")
    void invalidToken_ClearsContext() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.jwt.token");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("malformed Bearer token clears authentication")
    void malformedBearerToken_ClearsContext() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("filter chain is always called regardless of token validity")
    void filterChain_AlwaysCalled() throws Exception {
        // No token
        when(request.getHeader("Authorization")).thenReturn(null);
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(1)).doFilter(request, response);

        // Invalid token
        when(request.getHeader("Authorization")).thenReturn("Bearer garbage");
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        // filterChain called twice total
        verify(filterChain, times(2)).doFilter(request, response);

        // Valid token
        String token = jwtTokenProvider.createAccessToken(USER_ID, EMAIL, ROLE);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(3)).doFilter(request, response);
    }

    @Test
    @DisplayName("token signed with different secret fails authentication")
    void tokenWithDifferentSecret_FailsAuthentication() throws Exception {
        Algorithm differentAlgo = Algorithm.HMAC256("different-secret-key-for-testing-purposes-only");
        String token = JWT.create()
                .withSubject(USER_ID)
                .withClaim("email", EMAIL)
                .withClaim("role", ROLE)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(differentAlgo);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }

    @Test
    @DisplayName("expired token fails authentication")
    void expiredToken_FailsAuthentication() throws Exception {
        Algorithm algo = Algorithm.HMAC256(SECRET);
        String token = JWT.create()
                .withSubject(USER_ID)
                .withClaim("email", EMAIL)
                .withClaim("role", ROLE)
                .withIssuedAt(new Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
                .withExpiresAt(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .sign(algo);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }
}
