package com.devbraid.analysis.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.analysis.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates risk analysis: deterministic rules + commit analysis + test gap detection + optional AI enhancement.
 * Gracefully degrades to deterministic-only if AI is unavailable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAnalysisService {

    private final RiskFlagRules riskFlagRules;
    private final EvidenceExtractor evidenceExtractor;
    private final CommitMessageAnalyzer commitMessageAnalyzer;
    private final TestCoverageGapDetector testCoverageGapDetector;
    private final AutoDecisionNotes autoDecisionNotes;
    private final AIProvider aiProvider;
    private final PromptBuilder promptBuilder;

    /**
     * Run full risk analysis (deterministic + commit + test gap + AI).
     *
     * @param commitsJson      serialized commits JSON
     * @param changedFilesJson serialized changed files JSON
     * @return combined risk report map with flags, evidence, and AI insights
     */
    public Map<String, Object> analyze(String commitsJson, String changedFilesJson) throws Exception {
        Map<String, Object> report = new HashMap<>();

        // Phase 1: Deterministic rules (always runs)
        List<RiskFlagDto> flags = riskFlagRules.evaluate(commitsJson, changedFilesJson);
        Map<String, Object> evidence = evidenceExtractor.extract(commitsJson, changedFilesJson);
        RiskLevel overallRisk = riskFlagRules.calculateOverallRisk(flags);

        report.put("flags", flags);
        report.put("evidence", evidence);
        report.put("overallRisk", overallRisk);
        report.put("aiAnalyzed", false);

        // Phase 2: Commit message analysis (always runs)
        try {
            var commitAnalysis = commitMessageAnalyzer.analyzeCommits(commitsJson);
            report.put("commitAnalysis", Map.of(
                    "intentCounts", commitAnalysis.intentCounts(),
                    "hasBreakingChange", commitAnalysis.hasBreakingChange(),
                    "breakingChanges", commitAnalysis.breakingChanges(),
                    "insightCount", commitAnalysis.insights().size()
            ));
            List<String> commitRiskIndicators = commitMessageAnalyzer.getRiskIndicators(commitAnalysis);
            if (!commitRiskIndicators.isEmpty()) {
                report.put("commitRiskIndicators", commitRiskIndicators);
            }
        } catch (Exception e) {
            log.warn("Commit analysis failed: {}", e.getMessage());
        }

        // Phase 3: Test coverage gap detection (always runs)
        try {
            var testGaps = testCoverageGapDetector.analyzeTestCoverage(changedFilesJson);
            report.put("testCoverage", Map.of(
                    "productionFiles", testGaps.productionFileCount(),
                    "testFiles", testGaps.testFileCount(),
                    "highRiskUntested", testGaps.highRiskUntestedFiles(),
                    "recommendations", testGaps.recommendations()
            ));
        } catch (Exception e) {
            log.warn("Test coverage analysis failed: {}", e.getMessage());
        }

        // Phase 4: Auto-generated decision note suggestions (always runs)
        try {
            var suggestedNotes = autoDecisionNotes.suggestNotes(commitsJson, changedFilesJson);
            report.put("suggestedNotes", suggestedNotes.stream()
                    .map(n -> Map.of("category", n.category(), "content", n.content(), "source", n.source().name()))
                    .toList());
        } catch (Exception e) {
            log.warn("Auto decision notes generation failed: {}", e.getMessage());
        }

        // Phase 5: AI analysis — gracefully degrade if unavailable
        try {
            String aiResult = aiProvider.analyze(promptBuilder.buildAnalysisPrompt(commitsJson, changedFilesJson, flags));
            report.put("aiAnalysis", aiResult);
            report.put("aiAnalyzed", true);
        } catch (Exception e) {
            log.warn("AI analysis unavailable, using deterministic-only results: {}", e.getMessage());
            report.put("aiAnalyzed", false);
            report.put("aiError", e.getMessage());
        }

        return report;
    }
}
