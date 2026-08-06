package com.devbraid.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring's idiomatic HTTP client builder for outbound calls (GitHub API, etc.).
 * RestClient ships in spring-web (already on the classpath) — no extra dependency.
 * GitHubApiClient migration from java.net.http to RestClient remains out of scope.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
