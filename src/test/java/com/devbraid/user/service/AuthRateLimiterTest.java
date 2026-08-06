package com.devbraid.user.service;

import com.devbraid.user.exception.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("AuthRateLimiter Unit Tests")
@ExtendWith(MockitoExtension.class)
class AuthRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AuthRateLimiter limiter(long counts) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(counts);
        return new AuthRateLimiter(redisTemplate);
    }

    @Test
    @DisplayName("allows attempts under the limit")
    void underLimit_DoesNotThrow() {
        assertThatCode(() -> limiter(1L).checkLogin("user@example.com", "1.2.3.4"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("throws once the login limit is exceeded")
    void loginOverLimit_Throws() {
        assertThatThrownBy(() -> limiter(6L).checkLogin("user@example.com", "1.2.3.4"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("throws once the register limit is exceeded")
    void registerOverLimit_Throws() {
        assertThatThrownBy(() -> limiter(4L).checkRegister("user@example.com", "1.2.3.4"))
                .isInstanceOf(RateLimitExceededException.class);
    }
}