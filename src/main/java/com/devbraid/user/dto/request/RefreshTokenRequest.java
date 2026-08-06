package com.devbraid.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional refresh token payload. When omitted, the refresh token is read from the `refreshToken` httpOnly cookie.")
public class RefreshTokenRequest {

    // Optional — can also come from httpOnly cookie
    @Schema(description = "Refresh token issued at login/refresh. May be omitted when the cookie is present.",
            example = "eyJhbGciOiJIUzI1NiJ9...", nullable = true)
    private String refreshToken;
}
