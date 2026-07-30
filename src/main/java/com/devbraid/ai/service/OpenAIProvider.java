package com.devbraid.ai.service;

import com.devbraid.ai.config.AIConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Component
@ConditionalOnBean(AIConfig.class)
public class OpenAIProvider implements AIProvider {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final AIConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIProvider(AIConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String analyze(String prompt) throws Exception {
        if (!config.isOpenAiConfigured()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        var requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "model", config.getOpenAiModel(),
                        "messages", new Object[]{
                                java.util.Map.of("role", "system", "content", "You are a code review analyst. Analyze code changes and provide structured risk assessments."),
                                java.util.Map.of("role", "user", "content", prompt)
                        },
                        "temperature", 0.3,
                        "max_tokens", 1000
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + config.getOpenAiApiKey())
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("OpenAI API returned {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("OpenAI API error: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
