package com.devbraid.user.service;

import com.devbraid.common.util.OtpGenerator;
import com.devbraid.user.exception.OtpExpiredException;
import com.devbraid.user.exception.OtpInvalidException;
import com.devbraid.user.exception.OtpRateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final String OTP_PREFIX = "otp:";
    private static final String OTP_VERIFIED_PREFIX = "otp_verified:";
    private static final String RATE_LIMIT_PREFIX = "otp_rate:";
    private static final String PENDING_USER_PREFIX = "pending_user:";
    private static final String PENDING_USER_SEPARATOR = "||";
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RATE_LIMIT_TTL = Duration.ofMinutes(1);
    // ponytail: matches otp_verified TTL — pending data self-destructs as fast as the OTP window
    private static final Duration PENDING_USER_TTL = Duration.ofMinutes(10);
    private static final int MAX_OTP_REQUESTS_PER_MINUTE = 3;
    private final StringRedisTemplate redisTemplate;

    public void generateAndStoreOtp(String email) {
        String rateLimitKey = RATE_LIMIT_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, RATE_LIMIT_TTL);
        }
        if (count != null && count > MAX_OTP_REQUESTS_PER_MINUTE) {
            throw new OtpRateLimitException("Too many OTP requests. Please wait a minute.");
        }

        String otp = OtpGenerator.generate();
        String otpKey = OTP_PREFIX + email;
        redisTemplate.opsForValue().set(otpKey, otp, OTP_TTL);

        log.info("OtpService :: Generated OTP for email: {} (dev mode: {})", email, otp);
    }

    public void verifyOtp(String email, String otp) {
        String otpKey = OTP_PREFIX + email;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            throw new OtpExpiredException("OTP has expired or was not generated.");
        }

        if (!storedOtp.equals(otp)) {
            throw new OtpInvalidException("Invalid OTP. Please try again.");
        }

        redisTemplate.delete(otpKey);
        redisTemplate.opsForValue().set(OTP_VERIFIED_PREFIX + email, "true", Duration.ofMinutes(10));
    }

    // ponytail: pipe-delimited instead of JSON — 2 fields don't need a serializer
    public void storePendingRegistration(String email, String passwordHash, String fullName) {
        String value = passwordHash + PENDING_USER_SEPARATOR + fullName;
        redisTemplate.opsForValue().set(PENDING_USER_PREFIX + email, value, PENDING_USER_TTL);
        log.info("OtpService :: Stored pending registration for email: {}", email);
    }

    public PendingUser getPendingRegistration(String email) {
        String raw = redisTemplate.opsForValue().get(PENDING_USER_PREFIX + email);
        if (raw == null) return null;
        int sep = raw.indexOf(PENDING_USER_SEPARATOR);
        if (sep < 0) return null;
        return new PendingUser(raw.substring(0, sep), raw.substring(sep + PENDING_USER_SEPARATOR.length()));
    }

    public void deletePendingRegistration(String email) {
        redisTemplate.delete(PENDING_USER_PREFIX + email);
    }

    public boolean isEmailVerified(String email) {
        return "true".equals(redisTemplate.opsForValue().get(OTP_VERIFIED_PREFIX + email));
    }

    public void clearVerification(String email) {
        redisTemplate.delete(OTP_VERIFIED_PREFIX + email);
    }

    public record PendingUser(String passwordHash, String fullName) {
    }
}
