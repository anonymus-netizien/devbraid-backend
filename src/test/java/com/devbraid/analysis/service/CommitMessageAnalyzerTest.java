package com.devbraid.analysis.service;

import com.devbraid.github.dto.response.CommitSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommitMessageAnalyzerTest {

    private CommitMessageAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new CommitMessageAnalyzer();
    }

    private static CommitSummaryDto commit(String message) {
        return new CommitSummaryDto("abc123", message, null);
    }

    @Test
    void analyzeSingleCommit_conventionalCommitParsed() {
        var insight = analyzer.analyzeSingleCommit("feat(auth): add OAuth login support");

        assertEquals("feat", insight.type());
        assertEquals("auth", insight.scope());
        assertEquals("add OAuth login support", insight.summary());
        assertTrue(insight.semanticTags().contains("security"));
        assertFalse(insight.breakingChange());
    }

    @Test
    void analyzeSingleCommit_breakingChangeDetected() {
        var insight = analyzer.analyzeSingleCommit("feat(api)!: remove v1 endpoints");

        assertEquals("feat", insight.type());
        assertEquals("api", insight.scope());
        assertTrue(insight.breakingChange());
    }

    @Test
    void analyzeSingleCommit_noPrefixFreeform() {
        var insight = analyzer.analyzeSingleCommit("fixed the login bug");

        assertNull(insight.type());
        assertEquals("fixed the login bug", insight.summary());
    }

    @Test
    void analyzeSingleCommit_securityKeywordsDetected() {
        var insight = analyzer.analyzeSingleCommit("fix(security): patch XSS vulnerability in header");

        assertTrue(insight.semanticTags().contains("security"));
    }

    @Test
    void analyzeSingleCommit_databaseKeywordsDetected() {
        var insight = analyzer.analyzeSingleCommit("feat(db): add migration for user_preferences table");

        assertTrue(insight.semanticTags().contains("database"));
    }

    @Test
    void analyzeCommits_emptyInput_returnsEmptyResult() {
        var result = analyzer.analyzeCommits(null);
        assertTrue(result.insights().isEmpty());
        assertFalse(result.hasBreakingChange());

        var emptyResult = analyzer.analyzeCommits(List.of());
        assertTrue(emptyResult.insights().isEmpty());
    }

    @Test
    void analyzeCommits_multipleCommits_countsIntents() {
        List<CommitSummaryDto> commits = List.of(
                commit("feat(auth): add OAuth"),
                commit("fix(security): patch XSS"),
                commit("test(auth): add login tests")
        );

        var result = analyzer.analyzeCommits(commits);

        assertEquals(3, result.insights().size());
        assertTrue(result.intentCounts().getOrDefault("security", 0) >= 2);
        assertTrue(result.intentCounts().getOrDefault("testing", 0) >= 1);
    }

    @Test
    void getRiskIndicators_breakingChange() {
        var result = new CommitMessageAnalyzer.CommitAnalysisResult(
                List.of(new CommitMessageAnalyzer.CommitInsight("feat", "api", "api", "remove v1", List.of(), true, 20)),
                java.util.Map.of(), true, List.of("remove v1")
        );

        var indicators = analyzer.getRiskIndicators(result);
        assertTrue(indicators.stream().anyMatch(i -> i.contains("BREAKING CHANGE")));
    }

    @Test
    void getRiskIndicators_securityCommits() {
        var result = new CommitMessageAnalyzer.CommitAnalysisResult(
                List.of(new CommitMessageAnalyzer.CommitInsight("fix", "security", "security", "patch", List.of("security"), false, 20)),
                java.util.Map.of("security", 1), false, List.of()
        );

        var indicators = analyzer.getRiskIndicators(result);
        assertTrue(indicators.stream().anyMatch(i -> i.contains("Security")));
    }
}
