package com.devbraid.analysis.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Isolates the AI phase of risk analysis.
 * On AI failure returns {@code null}, letting {@link RiskAnalysisService}
 * keep the deterministic-only report — no AOP, just a guard clause.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRiskAnalysis {

    private final AIProvider aiProvider;
    private final PromptBuilder promptBuilder;

    public String analyze(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles,
                          List<RiskFlagDto> flags) {
        try {
            return aiProvider.analyze(promptBuilder.buildAnalysisPrompt(commits, changedFiles, flags));
        } catch (Exception ex) {
            log.warn("AI risk analysis failed — keeping deterministic-only report: {}", ex.getMessage());
            return null;
        }
    }
}
