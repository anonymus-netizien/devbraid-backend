package com.devbraid.analysis.service;

import com.devbraid.ai.fallback.FallbackMethod;
import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Isolates the AI phase of risk analysis behind a {@code @FallbackMethod}.
 * On AI failure the aspect returns {@code null} (via {@link #noAiAnalysis}),
 * letting {@link RiskAnalysisService} keep the deterministic-only report.
 */
@Component
@RequiredArgsConstructor
public class AiRiskAnalysis {

    private final AIProvider aiProvider;
    private final PromptBuilder promptBuilder;

    @FallbackMethod(method = "noAiAnalysis")
    public String analyze(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles,
                          List<RiskFlagDto> flags) throws Exception {
        return aiProvider.analyze(promptBuilder.buildAnalysisPrompt(commits, changedFiles, flags));
    }

    /**
     * Fallback: AI unavailable → null, so the caller keeps the deterministic-only report.
     */
    public String noAiAnalysis(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles,
                               List<RiskFlagDto> flags) {
        return null;
    }
}
