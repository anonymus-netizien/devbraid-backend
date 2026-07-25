package com.devbraid.common.util;

import java.security.SecureRandom;

public final class OtpGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    private OtpGenerator() {}

    public static String generate() {
        int min = (int) Math.pow(10, OTP_LENGTH - 1);
        int max = (int) Math.pow(10, OTP_LENGTH) - 1;
        return String.valueOf(SECURE_RANDOM.nextInt(max - min + 1) + min);
    }
}
