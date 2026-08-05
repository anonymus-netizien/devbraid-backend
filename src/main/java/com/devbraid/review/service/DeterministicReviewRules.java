package com.devbraid.review.service;

import com.devbraid.analysis.RiskLevel;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.analysis.service.CommitMessageAnalyzer;
import com.devbraid.analysis.service.RiskFlagRules;
import com.devbraid.analysis.service.TestCoverageGapDetector;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import com.devbraid.review.entity.FindingCategory;
import com.devbraid.review.entity.FindingSeverity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic review rules — reuses the existing analysis analyzers to produce
 * file-level findings that always run, before and independent of the AI phase.
 */
@Component
@RequiredArgsConstructor
public class DeterministicReviewRules {

    private final RiskFlagRules riskFlagRules;
    private final TestCoverageGapDetector testCoverageGapDetector;
    private final CommitMessageAnalyzer commitMessageAnalyzer;

    private static FindingSeverity toSeverity(RiskLevel level) {
        return switch (level) {
            case CRITICAL -> FindingSeverity.CRITICAL;
            case HIGH -> FindingSeverity.HIGH;
            case MEDIUM -> FindingSeverity.MEDIUM;
            case LOW, NONE -> FindingSeverity.LOW;
        };
    }

    private static FindingCategory categoryFor(String rule) {
        return switch (rule == null ? "" : rule) {
            case "securityPaths" -> FindingCategory.SECURITY;
            case "noTests" -> FindingCategory.TESTING;
            case "migrationFiles" -> FindingCategory.CORRECTNESS;
            case "crossCuttingConcern", "pomDependencyChange", "npmDependencyChange" -> FindingCategory.MAINTAINABILITY;
            case "revertCommits" -> FindingCategory.CORRECTNESS;
            default -> FindingCategory.OTHER;
        };
    }

    public List<ReviewFinding> evaluate(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles) {
        List<ReviewFinding> findings = new ArrayList<>();

        for (RiskFlagDto flag : riskFlagRules.evaluate(commits, changedFiles)) {
            findings.add(new ReviewFinding(
                    toSeverity(flag.getSeverity()),
                    categoryFor(flag.getRule()),
                    null, null,
                    flag.getRule(),
                    flag.getMessage()));
        }

        var testGaps = testCoverageGapDetector.analyzeTestCoverage(changedFiles);
        for (String file : testGaps.highRiskUntestedFiles()) {
            findings.add(new ReviewFinding(FindingSeverity.HIGH, FindingCategory.TESTING,
                    file, null, "High-risk file without tests",
                    "High-risk production change with no corresponding test coverage."));
        }

        var commitAnalysis = commitMessageAnalyzer.analyzeCommits(commits);
        for (String breaking : commitAnalysis.breakingChanges()) {
            findings.add(new ReviewFinding(FindingSeverity.MEDIUM, FindingCategory.DOCUMENTATION,
                    null, null, "Breaking change", breaking));
        }
        for (String indicator : commitMessageAnalyzer.getRiskIndicators(commitAnalysis)) {
            findings.add(new ReviewFinding(FindingSeverity.MEDIUM, FindingCategory.OTHER,
                    null, null, "Commit review indicator", indicator));
        }

        return findings;
    }
}
