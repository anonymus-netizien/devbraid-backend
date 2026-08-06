package com.devbraid.brief.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
import com.devbraid.changethread.entity.ChangeThread;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BriefContentGenerator {

    private final AIProvider aiProvider;
    private final PromptBuilder promptBuilder;

    /**
     * Generate brief content via AI, falling back to the template on AI failure
     * or when the output lacks citation/inference markers.
     */
    public String generateContent(ChangeThread thread) {
        try {
            String content = aiProvider.analyze(promptBuilder.buildBriefPrompt(thread));
            if (!isEvidenceBacked(content)) {
                log.warn("AI brief output missing citation/inference markers — using template fallback");
                return buildTemplate(thread);
            }
            return content;
        } catch (Exception ex) {
            log.warn("AI brief generation failed — using template fallback: {}", ex.getMessage());
            return buildTemplate(thread);
        }
    }

    private String buildTemplate(ChangeThread thread) {
        try {
            return promptBuilder.buildTemplateBrief(thread);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Template brief generation failed", ex);
        }
    }

    private boolean isEvidenceBacked(String content) {
        if (content == null || content.isBlank()) return false;
        String lower = content.toLowerCase();
        return lower.contains("[file:") || lower.contains("[commit:")
                || lower.contains("[source:") || lower.contains("[inference]");
    }
}
