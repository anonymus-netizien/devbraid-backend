package com.devbraid.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ponytail: Lombok with @Builder should generate builder(), but Java 25
 * has compatibility issues with this Lombok version.
 * Explicit static builder() method is defensive — remove when Lombok updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerifyResponse {
    private String email;
    private boolean verified;

    // Explicit builder method for Java 25 Lombok compatibility
    public static OtpVerifyResponseBuilder builder() {
        return new OtpVerifyResponseBuilder();
    }
}
