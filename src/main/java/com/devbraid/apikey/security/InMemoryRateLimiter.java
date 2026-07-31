package com.devbraid.apikey.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter keyed by API-key hash, in-process only.
 * ponytail: single-instance MVP — a ConcurrentHashMap window beats a Redis
 * dependency at $0 infra. Swap to Redis-backed when we scale to multiple instances.
 */
@Component
public class InMemoryRateLimiter {

    private static final long WINDOW_MS = 60_000L;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /**
     * @return true if the request is allowed within limitPerMin, false if over the window.
     */
    public boolean isAllowed(String key, int limitPerMin) {
        if (limitPerMin <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Deque<Long> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= limitPerMin) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /**
     * Seconds until the window resets for a key — used for the Retry-After header.
     */
    public long retryAfterSeconds(String key) {
        Deque<Long> window = windows.get(key);
        if (window == null || window.isEmpty()) {
            return 0;
        }
        synchronized (window) {
            long oldest = window.peekFirst();
            long remaining = WINDOW_MS - (System.currentTimeMillis() - oldest);
            return Math.max(0, (remaining + 999) / 1000);
        }
    }
}
