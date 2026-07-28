package com.devbraid.analysis.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.changethread.entity.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates risk analysis: deterministic rules + optional AI enhancement.
 * Gracefully degrades to deterministic-only if AI is unavailable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAnalysisService {

    private final RiskFlagRules riskFlagRules;
    private final EvidenceExtractor evidenceExtractor;
    private final AIProvider aiProvider;

    /**
     * Run full risk analysis (deterministic + AI).
     *
     * @param commitsJson      serialized commits JSON
     * @param changedFilesJson serialized changed files JSON
     * @return combined risk report map with flags, evidence, and AI insights
     */
    public Map<String, Object> analyze(String commitsJson, String changedFilesJson) {
        Map<String, Object> report = new HashMap<>();

        // Phase 1: Deterministic rules (always runs)
        List<RiskFlagDto> flags = riskFlagRules.evaluate(commitsJson, changedFilesJson);
        Map<String, Object> evidence = evidenceExtractor.extract(commitsJson, changedFilesJson);
        RiskLevel overallRisk = riskFlagRules.calculateOverallRisk(flags);

        report.put("flags", flags);
        report.put("evidence", evidence);
        report.put("overallRisk", overallRisk);
        report.put("aiAnalyzed", false);

        // Phase 2: AI analysis (optional, graceful degradation)
        try {
            String aiResult = aiProvider.analyze(buildAnalysisPrompt(commitsJson, changedFilesJson, flags));
            report.put("aiAnalysis", aiResult);
            report.put("aiAnalyzed", true);
        } catch (Exception e) {
            log.warn("AI analysis unavailable, using deterministic-only: {}", e.getMessage());
            report.put("aiAnalysis", null);
            report.put("aiError", "AI analysis unavailable — using deterministic rules only");
        }

        return report;
    }

    private String buildAnalysisPrompt(String commitsJson, String changedFilesJson, List<RiskFlagDto> flags) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze these code changes and provide a structured risk assessment.\n\n");
        prompt.append("Changed files:\n").append(changedFilesJson != null ? changedFilesJson : "N/A").append("\n\n");
        prompt.append("Commits:\n").append(commitsJson != null ? commitsJson : "N/A").append("\n\n");
        prompt.append("Pre-computed risk flags:\n");
        for (RiskFlagDto flag : flags) {
            prompt.append("- ").append(flag.getRule()).append(": ").append(flag.getMessage()).append("\n");
        }
        prompt.append("\nProvide: 1) Risk summary 2) Key concerns 3) Recommendations");
        return prompt.toString();
    }
}
