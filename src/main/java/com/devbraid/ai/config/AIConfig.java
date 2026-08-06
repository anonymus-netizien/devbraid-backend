package com.devbraid.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized AI provider configuration.
 * Supports OpenAI, Groq, and OpenRouter — select via {@code ai.provider}.
 * When the chosen provider's key is missing, the application falls back
 * to deterministic-only analysis (no AI).
 */
@Configuration
public class AIConfig {

    @Value("${ai.provider:openai}")
    private String provider;

    // ── OpenAI ──
    @Value("${ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${ai.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    // ── Groq (free tier, fast inference) ──
    @Value("${ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${ai.groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${ai.groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    // ── OpenRouter (multi-model gateway) ──
    @Value("${ai.openrouter.api-key:}")
    private String openRouterApiKey;

    @Value("${ai.openrouter.model:meta-llama/llama-3.3-70b-instruct:free}")
    private String openRouterModel;

    @Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    // ── Unified timeout ──
    @Value("${ai.timeout-seconds:30}")
    private int timeoutSeconds;

    // ── Provider selection ──

    public String getProvider() {
        return provider;
    }

    // ── OpenAI ──

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public String getOpenAiModel() {
        return openAiModel;
    }

    public String getOpenAiBaseUrl() {
        return openAiBaseUrl;
    }

    // ── Groq ──

    public String getGroqApiKey() {
        return groqApiKey;
    }

    public String getGroqModel() {
        return groqModel;
    }

    public String getGroqBaseUrl() {
        return groqBaseUrl;
    }

    // ── OpenRouter ──

    public String getOpenRouterApiKey() {
        return openRouterApiKey;
    }

    public String getOpenRouterModel() {
        return openRouterModel;
    }

    public String getOpenRouterBaseUrl() {
        return openRouterBaseUrl;
    }

    // ── Timeout ──

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
