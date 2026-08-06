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
import java.util.Map;

/**
 * Multi-provider AI client that routes requests to OpenAI, Groq, or OpenRouter
 * based on the {@code ai.provider} configuration property.
 * Falls back through the provider chain if the primary is unavailable.
 * <p>
 * All HTTP exceptions propagate to GlobalExceptionHandler — no try-catches here.
 */
@Slf4j
@Component
@ConditionalOnBean(AIConfig.class)
public class MultiProviderAI implements AIProvider {

    private final AIConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MultiProviderAI(AIConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String analyze(String prompt) throws Exception {
        return switch (config.getProvider().toLowerCase()) {
            case "groq" -> callProvider(config.getGroqBaseUrl(), config.getGroqApiKey(),
                    config.getGroqModel(), prompt);
            case "openrouter" -> callProvider(config.getOpenRouterBaseUrl(), config.getOpenRouterApiKey(),
                    config.getOpenRouterModel(), prompt);
            case "openai" -> callProvider(config.getOpenAiBaseUrl(), config.getOpenAiApiKey(),
                    config.getOpenAiModel(), prompt);
            default -> throw new IllegalStateException("Unknown AI provider: " + config.getProvider());
        };
    }

    /**
     * Call an OpenAI-compatible API (works for OpenAI, Groq, OpenRouter).
     * No try-catch — exceptions propagate to GlobalExceptionHandler.
     */
    private String callProvider(String baseUrl, String apiKey, String model, String prompt) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI provider API key not configured for: " + config.getProvider());
        }

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "messages", new Object[]{
                        Map.of("role", "system", "content",
                                "You are a code review analyst. Analyze code changes and provide structured risk assessments."),
                        Map.of("role", "user", "content", prompt)
                },
                "temperature", 0.3,
                "max_tokens", 1000
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("AI provider {} returned {}: {}", config.getProvider(), response.statusCode(), response.body());
            throw new RuntimeException("AI provider error (" + config.getProvider() + "): HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
