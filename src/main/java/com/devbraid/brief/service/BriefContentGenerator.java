package com.devbraid.brief.service;

import com.devbraid.ai.fallback.FallbackMethod;
import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
import com.devbraid.changethread.entity.ChangeThread;
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
     * (handled by {@code @FallbackMethod} aspect — no try/catch in services).
     * Also falls back to the template when AI output lacks citation/inference markers.
     *
     * @throws Exception if AI fails and the fallback aspect is not active
     */
    @FallbackMethod(method = "buildTemplate")
    public String generateContent(ChangeThread thread) throws Exception {
        String content = aiProvider.analyze(promptBuilder.buildBriefPrompt(thread));
        if (!isEvidenceBacked(content)) {
            log.warn("AI brief output missing citation/inference markers — using template fallback");
            return promptBuilder.buildTemplateBrief(thread);
        }
        return content;
    }

    /**
     * Fallback invoked by the aspect when AI fails.
     */
    public String buildTemplate(ChangeThread thread) throws Exception {
        return promptBuilder.buildTemplateBrief(thread);
    }

    private boolean isEvidenceBacked(String content) {
        if (content == null || content.isBlank()) return false;
        String lower = content.toLowerCase();
        return lower.contains("[file:") || lower.contains("[commit:")
                || lower.contains("[source:") || lower.contains("[inference]");
    }
}
