package com.devbraid.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 documentation configuration for the DevBraid API.
 *
 * <p>Generates the OpenAPI specification served at {@code /v3/api-docs} and renders
 * the interactive Swagger UI at {@code /swagger-ui.html}. The JWT bearer token is
 * declared as the security scheme; each protected controller/operation opts in via
 * {@code @SecurityRequirement}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "DevBraid API",
                version = "v1",
                description = """
                        DevBraid captures the *why* behind code changes. Developers connect a GitHub
                        repository with a personal access token, create **Change Threads** describing a
                        change (branch pair), and the platform pulls commits and diffs, runs deterministic
                        risk analysis, generates Markdown change briefs (AI or template), and publishes them
                        back to GitHub as PR comments after human approval.
                        
                        ## Authentication
                        
                        All endpoints except `POST /api/v1/auth/*` require a JWT bearer token obtained from
                        `POST /api/v1/auth/login`. Click **Authorize** and enter your access token.
                        
                        ## Response envelope
                        
                        Every endpoint returns an `ApiResponse` wrapper: `{ "success": boolean,
                        "message": string, "data": T | null }`. Errors are mapped to standard HTTP status
                        codes by the global exception handler (400, 401, 403, 404, 409, 410, 429, 500).
                        """,
                contact = @Contact(
                        name = "DevBraid",
                        url = "https://github.com/anonymus-netizien/devbraid-backend",
                        email = "support@devbraid.com"),
                license = @License(name = "Proprietary")
        ),
        servers = @Server(url = "/", description = "Current environment (relative to deployed context)")
)
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT access token returned by POST /api/v1/auth/login. Set the refresh token "
                + "cookie separately; the access token is supplied in the Authorization header.")
public class OpenAPIConfig {
}
