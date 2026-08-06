package com.devbraid.analysis.service;

import com.devbraid.github.dto.response.ChangedFileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects test coverage gaps by analyzing changed files.
 * Flags when production code changes lack corresponding test updates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestCoverageGapDetector {

    // File patterns that represent production code
    private static final Pattern PRODUCTION_FILE = Pattern.compile(
            "\\.(java|ts|tsx|js|jsx|py|go|rs)$",
            Pattern.CASE_INSENSITIVE
    );
    // Patterns that identify test files
    private static final List<Pattern> TEST_FILE_PATTERNS = List.of(
            Pattern.compile("Test\\.(java|ts)$"),
            Pattern.compile("\\.test\\.(ts|tsx|js|jsx)$"),
            Pattern.compile("\\.spec\\.(ts|tsx|js|jsx)$"),
            Pattern.compile("/__tests__/"),
            Pattern.compile("/test/"),
            Pattern.compile("/tests/"),
            Pattern.compile("/__mocks__/")
    );
    // Patterns that suggest high-risk code (needs tests more urgently)
    private static final Pattern HIGH_RISK_PATH = Pattern.compile(
            "(/security/|/auth/|/crypto/|/payment/|/config/|Controller\\.java|Service\\.java|Repository\\.java)",
            Pattern.CASE_INSENSITIVE
    );
    // Patterns for infrastructure/config that usually don't need unit tests
    private static final Pattern INFRA_FILE = Pattern.compile(
            "(application.*\\.yml|application.*\\.properties|Dockerfile|docker-compose|pom\\.xml|package\\.json|\\.env)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Analyze typed changed files for test coverage gaps — no JSON parsing.
     */
    public TestGapResult analyzeTestCoverage(List<ChangedFileDto> changedFiles) {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return new TestGapResult(List.of(), 0, 0, List.of());
        }

        // ponytail: two-pass scan — first pass classifies all files into sets,
        // second pass checks hasCorrespondingTest against the fully populated test set.
        // The old single-pass approach had an order-dependency bug: if Foo.java
        // appeared before FooTest.java in the changedFiles list, it was flagged
        // as untested because the test file hadn't been collected yet.
        List<String> productionFiles = new ArrayList<>();
        List<String> testFiles = new ArrayList<>();

        // Pass 1: classify all files
        for (ChangedFileDto file : changedFiles) {
            String filename = file.getFilename();
            if (filename == null) continue;

            if (isTestFile(filename)) {
                testFiles.add(filename);
            } else if (isProductionFile(filename)) {
                productionFiles.add(filename);
            }
        }

        // Pass 2: find high-risk untested files against the fully populated test set
        List<String> highRiskUntested = new ArrayList<>();
        for (ChangedFileDto file : changedFiles) {
            String filename = file.getFilename();
            if (filename == null) continue;
            if (!productionFiles.contains(filename)) continue;

            int additions = file.getAdditions();
            int deletions = file.getDeletions();
            int changes = additions + deletions;

            if (changes > 10 && !hasCorrespondingTest(filename, testFiles)) {
                if (HIGH_RISK_PATH.matcher(filename).find()) {
                    highRiskUntested.add(String.format("%s (+%d/-%d lines)", filename, additions, deletions));
                }
            }
        }

        List<String> recommendations = buildRecommendations(productionFiles, testFiles, highRiskUntested);

        return new TestGapResult(highRiskUntested, productionFiles.size(), testFiles.size(), recommendations);
    }

    private boolean isTestFile(String filename) {
        return TEST_FILE_PATTERNS.stream().anyMatch(p -> p.matcher(filename).find());
    }

    private boolean isProductionFile(String filename) {
        if (INFRA_FILE.matcher(filename).find()) return false;
        return PRODUCTION_FILE.matcher(filename).find();
    }

    private boolean hasCorrespondingTest(String prodFile, List<String> testFiles) {
        // Extract the base name for matching
        String baseName;
        if (prodFile.endsWith(".java")) {
            baseName = prodFile.substring(prodFile.lastIndexOf('/') + 1).replace(".java", "");
        } else if (prodFile.endsWith(".ts") || prodFile.endsWith(".tsx")) {
            baseName = prodFile.substring(prodFile.lastIndexOf('/') + 1)
                    .replace(".tsx", "").replace(".ts", "");
        } else {
            return false;
        }

        String finalBaseName = baseName;
        return testFiles.stream().anyMatch(t -> t.contains(finalBaseName));
    }

    private List<String> buildRecommendations(List<String> prodFiles, List<String> testFiles, List<String> highRisk) {
        List<String> recs = new ArrayList<>();

        if (testFiles.isEmpty() && !prodFiles.isEmpty()) {
            recs.add("No test files changed — add tests for production code changes");
        }
        if (!highRisk.isEmpty()) {
            recs.add(highRisk.size() + " high-risk file(s) without matching tests: " + String.join(", ", highRisk));
        }

        double ratio = prodFiles.isEmpty() ? 0 : (double) testFiles.size() / prodFiles.size();
        if (prodFiles.size() > 3 && ratio < 0.2) {
            recs.add("Low test-to-code ratio (" + String.format("%.0f%%", ratio * 100) + ") — consider adding more tests");
        }

        return recs;
    }

    // ── Result DTO ──────────────────────────────────────────────────

    public record TestGapResult(
            List<String> highRiskUntestedFiles,
            int productionFileCount,
            int testFileCount,
            List<String> recommendations
    ) {
    }
}
