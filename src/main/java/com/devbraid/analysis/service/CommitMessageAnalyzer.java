package com.devbraid.analysis.service;

import com.devbraid.github.dto.response.CommitSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes commit messages to extract intent, semantic tags, and scope.
 * Used to enrich risk analysis and brief generation with commit-level insights.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommitMessageAnalyzer {

    // Conventional commit prefix patterns
    private static final Pattern CONVENTIONAL_PREFIX = Pattern.compile(
            "^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(?:\\(([^)]+)\\))?(!)?:\\s*(.+)",
            Pattern.CASE_INSENSITIVE
    );
    // Keywords that indicate semantic intent
    private static final Map<String, List<String>> INTENT_KEYWORDS = Map.of(
            "security", List.of("security", "auth", "token", "encrypt", "decrypt", "password", "csrf", "xss", "sqli", "vulnerability"),
            "breaking", List.of("breaking", "migration", "schema", "drop", "remove api", "deprecat"),
            "performance", List.of("perf", "cache", "optimize", "speed", "slow", "latency", "memory"),
            "database", List.of("migration", "schema", "flyway", "liquibase", "table", "column", "index"),
            "testing", List.of("test", "spec", "coverage", "mock", "stub", "integration test"),
            "documentation", List.of("readme", "docs", "javadoc", "comment", "changelog"),
            "infrastructure", List.of("docker", "ci/cd", "deploy", "nginx", "kubernetes", "helm", "terraform")
    );
    // Patterns for detecting breaking changes
    private static final List<Pattern> BREAKING_PATTERNS = List.of(
            Pattern.compile("(?i)breaking\\s*change"),
            Pattern.compile("(?i)\\bdrop(ped)?\\b.*\\b(column|table|api|endpoint)\\b"),
            Pattern.compile("(?i)\\bremov(e|ed|ing)\\b.*\\b(api|endpoint|field|column)\\b"),
            Pattern.compile("(?i)\\bincompatible\\b"),
            Pattern.compile("(?i)\\bmigrate?d?\\b.*\\bfrom\\b.*\\bto\\b")
    );
    /**
     * Analyze typed commits and extract structured intent — no JSON parsing.
     */
    public CommitAnalysisResult analyzeCommits(List<CommitSummaryDto> commits) {
        if (commits == null || commits.isEmpty()) {
            return new CommitAnalysisResult(List.of(), Map.of(), false, List.of());
        }

        List<CommitInsight> insights = new ArrayList<>();
        Map<String, Integer> intentCounts = new HashMap<>();
        List<String> breakingChanges = new ArrayList<>();
        boolean hasBreakingChange = false;

        for (CommitSummaryDto commit : commits) {
            String message = commit.getMessage();
            if (message == null) continue;

            CommitInsight insight = analyzeSingleCommit(message);
            insights.add(insight);

            // Count intent categories
            for (String tag : insight.semanticTags()) {
                intentCounts.merge(tag, 1, Integer::sum);
            }

            // Track breaking changes
            if (insight.breakingChange()) {
                hasBreakingChange = true;
                breakingChanges.add(insight.summary());
            }
        }

        return new CommitAnalysisResult(insights, intentCounts, hasBreakingChange, breakingChanges);
    }

    /**
     * Analyze a single commit message.
     */
    public CommitInsight analyzeSingleCommit(String message) {
        String firstLine = message.contains("\n") ? message.substring(0, message.indexOf('\n')) : message;
        firstLine = firstLine.trim();

        String type = null;
        String scope = null;
        String summary = firstLine;
        boolean hasBreakingMarker = false;

        // Parse conventional commit prefix
        Matcher matcher = CONVENTIONAL_PREFIX.matcher(firstLine);
        if (matcher.matches()) {
            type = matcher.group(1).toLowerCase();
            scope = matcher.group(2);
            hasBreakingMarker = matcher.group(3) != null; // ! before :
            summary = matcher.group(4);
        }

        // Extract semantic tags
        List<String> semanticTags = new ArrayList<>();
        String lowerMessage = message.toLowerCase();
        for (Map.Entry<String, List<String>> entry : INTENT_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lowerMessage.contains(keyword)) {
                    semanticTags.add(entry.getKey());
                    break;
                }
            }
        }

        // Check for breaking changes
        boolean isBreaking = hasBreakingMarker;
        if (!isBreaking) {
            for (Pattern p : BREAKING_PATTERNS) {
                if (p.matcher(message).find()) {
                    isBreaking = true;
                    break;
                }
            }
        }

        // Detect scope category
        String scopeCategory = categorizeScope(scope);

        return new CommitInsight(type, scope, scopeCategory, summary, semanticTags, isBreaking, message.length());
    }

    /**
     * Get risk-relevant insights from commit analysis.
     */
    public List<String> getRiskIndicators(CommitAnalysisResult result) {
        List<String> indicators = new ArrayList<>();

        if (result.hasBreakingChange()) {
            indicators.add("BREAKING CHANGE detected — verify compatibility");
        }

        Map<String, Integer> intents = result.intentCounts();
        if (intents.getOrDefault("security", 0) > 0) {
            indicators.add("Security-related commits detected — extra review recommended");
        }
        if (intents.getOrDefault("database", 0) > 0) {
            indicators.add("Database changes detected — verify migrations");
        }
        if (intents.getOrDefault("infrastructure", 0) > 0) {
            indicators.add("Infrastructure changes detected — verify deployment");
        }

        // Check for large commits (potential squash needed)
        long largeCommits = result.insights().stream()
                .filter(i -> i.messageLength() > 500)
                .count();
        if (largeCommits > 0) {
            indicators.add(largeCommits + " large commit(s) — consider splitting");
        }

        // Check for mixed concerns in single commits
        long multiTagCommits = result.insights().stream()
                .filter(i -> i.semanticTags().size() > 2)
                .count();
        if (multiTagCommits > 0) {
            indicators.add(multiTagCommits + " commit(s) with mixed concerns — may hide risk");
        }

        return indicators;
    }

    private String categorizeScope(String scope) {
        if (scope == null) return "general";
        String lower = scope.toLowerCase();
        if (lower.contains("auth") || lower.contains("security")) return "security";
        if (lower.contains("api") || lower.contains("controller")) return "api";
        if (lower.contains("db") || lower.contains("repo") || lower.contains("entity")) return "data";
        if (lower.contains("ui") || lower.contains("frontend") || lower.contains("component")) return "ui";
        if (lower.contains("test") || lower.contains("spec")) return "test";
        if (lower.contains("config") || lower.contains("env")) return "config";
        return "general";
    }

    // ── Result DTOs ─────────────────────────────────────────────────

    public record CommitAnalysisResult(
            List<CommitInsight> insights,
            Map<String, Integer> intentCounts,
            boolean hasBreakingChange,
            List<String> breakingChanges
    ) {
    }

    public record CommitInsight(
            String type,
            String scope,
            String scopeCategory,
            String summary,
            List<String> semanticTags,
            boolean breakingChange,
            int messageLength
    ) {
    }
}
