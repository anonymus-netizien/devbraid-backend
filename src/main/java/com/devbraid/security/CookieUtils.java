package com.devbraid.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class CookieUtils {

    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    public static final String REFRESH_TOKEN_PATH = "/api/v1/auth/refresh";

    private CookieUtils() {
    }

    public static void addRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        String cookieValue = String.format(
                "%s=%s; Max-Age=%d; Path=%s; HttpOnly; Secure; SameSite=Lax",
                REFRESH_TOKEN_COOKIE, token, maxAgeSeconds, REFRESH_TOKEN_PATH);
        response.addHeader("Set-Cookie", cookieValue);
    }

    public static void clearRefreshTokenCookie(HttpServletResponse response) {
        String cookieValue = String.format(
                "%s=; Max-Age=0; Path=%s; HttpOnly; Secure; SameSite=Lax",
                REFRESH_TOKEN_COOKIE, REFRESH_TOKEN_PATH);
        response.addHeader("Set-Cookie", cookieValue);
    }

    public static String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
