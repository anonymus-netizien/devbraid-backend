package com.devbraid.analysis.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
import com.devbraid.analysis.RiskLevel;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
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
     * @param commits      typed commits
     * @param changedFiles typed changed files
     * @return combined risk report map with flags, evidence, and AI insights
     */
    public Map<String, Object> analyze(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles) throws Exception {
        Map<String, Object> report = new HashMap<>();

        // Phase 1: Deterministic rules (always runs)
        List<RiskFlagDto> flags = riskFlagRules.evaluate(commits, changedFiles);
        Map<String, Object> evidence = evidenceExtractor.extract(commits, changedFiles);
        RiskLevel overallRisk = riskFlagRules.calculateOverallRisk(flags);

        report.put("flags", flags);
        report.put("evidence", evidence);
        report.put("overallRisk", overallRisk);
        report.put("aiAnalyzed", false);

        // Phase 2: Commit message analysis (always runs)
        try {
            var commitAnalysis = commitMessageAnalyzer.analyzeCommits(commits);
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
            var testGaps = testCoverageGapDetector.analyzeTestCoverage(changedFiles);
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
            var suggestedNotes = autoDecisionNotes.suggestNotes(commits, changedFiles);
            report.put("suggestedNotes", suggestedNotes.stream()
                    .map(n -> Map.of("category", n.category(), "content", n.content(), "source", n.source().name()))
                    .toList());
        } catch (Exception e) {
            log.warn("Auto decision notes generation failed: {}", e.getMessage());
        }

        // Phase 5: AI analysis — gracefully degrade if unavailable
        try {
            String aiResult = aiProvider.analyze(promptBuilder.buildAnalysisPrompt(commits, changedFiles, flags));
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
