package com.devbraid.user.service;

import com.devbraid.user.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Redis-backed brute-force guard for the sign-up and login endpoints.
 * Tracks each attempt by both the email (user) and the client IP, reusing the
 * same increment-and-TTL counter pattern as {@link OtpService}.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimiter {

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final int MAX_LOGIN_PER_USER = 5;
    private static final int MAX_LOGIN_PER_IP = 20;
    private static final int MAX_REGISTER_PER_USER = 3;
    private static final int MAX_REGISTER_PER_IP = 10;

    private final StringRedisTemplate redisTemplate;

    public void checkLogin(String email, String ip) {
        check("auth:login:user:" + norm(email), MAX_LOGIN_PER_USER, LOGIN_WINDOW);
        check("auth:login:ip:" + ip, MAX_LOGIN_PER_IP, LOGIN_WINDOW);
    }

    public void checkRegister(String email, String ip) {
        check("auth:register:user:" + norm(email), MAX_REGISTER_PER_USER, REGISTER_WINDOW);
        check("auth:register:ip:" + ip, MAX_REGISTER_PER_IP, REGISTER_WINDOW);
    }

    private void check(String key, int max, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, window);
        }
        if (count != null && count > max) {
            throw new RateLimitExceededException("Too many attempts. Please try again later.");
        }
    }

    // ponytail: lowercase so case variation can't dodge the per-user counter
    private String norm(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}