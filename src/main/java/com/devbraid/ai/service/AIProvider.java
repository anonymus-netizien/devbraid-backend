package com.devbraid.ai.service;

/**
 * Interface for AI-powered analysis.
 * Implementations can use OpenAI, Anthropic, or other providers.
 */
public interface AIProvider {

    /**
     * Send a prompt to the AI and return the response.
     *
     * @param prompt the prompt text
     * @return the AI response text
     * @throws Exception if the AI service is unavailable
     */
    String analyze(String prompt) throws Exception;
}
