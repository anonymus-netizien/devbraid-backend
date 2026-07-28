package com.devbraid.analysis.service;

import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.changethread.entity.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic risk evaluation rules for code changes.
 * Each rule evaluates a specific risk pattern and returns a flag if triggered.
 */
@Slf4j
@Component
public class RiskFlagRules {

    private static final int LARGE_DIFF_THRESHOLD = 500;
    private static final int MANY_FILES_THRESHOLD = 20;
    private static final int SINGLE_FILE_LARGE_CHANGE_THRESHOLD = 200;

    /**
     * Evaluate all deterministic risk rules against the given commits and files.
     *
     * @param commitsJson      serialized commits JSON
     * @param changedFilesJson serialized changed files JSON
     * @return list of triggered risk flags, sorted by severity (highest first)
     */
    public List<RiskFlagDto> evaluate(String commitsJson, String changedFilesJson) {
        List<RiskFlagDto> flags = new ArrayList<>();

        // Parse file data from JSON
        List<FileData> files = parseFiles(changedFilesJson);
        List<CommitData> commits = parseCommits(commitsJson);

        // Rule: Large diff (total lines changed > 500)
        int totalLinesChanged = files.stream()
                .mapToInt(f -> f.additions + f.deletions)
                .sum();
        if (totalLinesChanged > LARGE_DIFF_THRESHOLD) {
            flags.add(RiskFlagDto.builder()
                    .rule("largeDiff")
                    .severity(RiskLevel.MEDIUM)
                    .message("Large diff: " + totalLinesChanged + " lines changed")
                    .evidence(List.of(totalLinesChanged + " lines across " + files.size() + " files"))
                    .build());
        }

        // Rule: Many files changed (> 20)
        if (files.size() > MANY_FILES_THRESHOLD) {
            flags.add(RiskFlagDto.builder()
                    .rule("manyFiles")
                    .severity(RiskLevel.MEDIUM)
                    .message("Large changeset: " + files.size() + " files")
                    .evidence(List.of(files.size() + " files modified"))
                    .build());
        }

        // Rule: Security-sensitive paths
        List<String> securityFiles = files.stream()
                .filter(f -> f.filename.contains("/security/") || f.filename.contains("/auth/") || f.filename.contains("/crypto/"))
                .map(f -> f.filename)
                .toList();
        if (!securityFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("securityPaths")
                    .severity(RiskLevel.HIGH)
                    .message("Security-related changes detected")
                    .evidence(securityFiles)
                    .build());
        }

        // Rule: No test files changed
        boolean hasTestFiles = files.stream()
                .anyMatch(f -> f.filename.endsWith("Test.java") || f.filename.endsWith("Test.ts")
                        || f.filename.endsWith(".test.java") || f.filename.endsWith(".test.ts")
                        || f.filename.contains("/test/") || f.filename.contains("/__tests__/"));
        if (!hasTestFiles && !files.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("noTests")
                    .severity(RiskLevel.LOW)
                    .message("No test files changed")
                    .evidence(List.of("Consider adding tests for these changes"))
                    .build());
        }

        // Rule: Config changes
        List<String> configFiles = files.stream()
                .filter(f -> f.filename.contains("/config/") || (f.filename.contains("application") && f.filename.endsWith(".yml")))
                .map(f -> f.filename)
                .toList();
        if (!configFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("configChanges")
                    .severity(RiskLevel.LOW)
                    .message("Configuration files changed")
                    .evidence(configFiles)
                    .build());
        }

        // Rule: Database migrations
        List<String> migrationFiles = files.stream()
                .filter(f -> f.filename.matches(".*V\\d+__.*\\.sql"))
                .map(f -> f.filename)
                .toList();
        if (!migrationFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("migrationFiles")
                    .severity(RiskLevel.MEDIUM)
                    .message("Database migration detected")
                    .evidence(migrationFiles)
                    .build());
        }

        // Rule: Dependency changes
        List<String> dependencyFiles = files.stream()
                .filter(f -> f.filename.equals("pom.xml") || f.filename.equals("package.json")
                        || f.filename.equals("package-lock.json") || f.filename.equals("yarn.lock"))
                .map(f -> f.filename)
                .toList();
        if (!dependencyFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("dependencyChanges")
                    .severity(RiskLevel.LOW)
                    .message("Dependency files changed")
                    .evidence(dependencyFiles)
                    .build());
        }

        // Sort by severity (highest first)
        flags.sort((a, b) -> b.getSeverity().compareTo(a.getSeverity()));

        return flags;
    }

    /**
     * Calculate overall risk level from a list of flags.
     */
    public RiskLevel calculateOverallRisk(List<RiskFlagDto> flags) {
        if (flags == null || flags.isEmpty()) {
            return RiskLevel.NONE;
        }
        return flags.stream()
                .map(RiskFlagDto::getSeverity)
                .max(RiskLevel::compareTo)
                .orElse(RiskLevel.NONE);
    }

    // ── JSON parsing helpers ─────────────────────────────────────────

    /**
     * Parse changed files JSON into FileData list.
     * Uses basic string parsing to avoid Jackson dependency in this component.
     */
    private List<FileData> parseFiles(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<FileData> files = new ArrayList<>();
        try {
            // Simple JSON array parsing for [{"filename":"...","additions":N,"deletions":N},...]
            String trimmed = json.trim();
            if (!trimmed.startsWith("[")) return List.of();
            String content = trimmed.substring(1, trimmed.length() - 1);

            int depth = 0;
            int start = 0;
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '{') depth++;
                if (content.charAt(i) == '}') depth--;
                if (depth == 0 && content.charAt(i) == '}') {
                    String obj = content.substring(start, i + 1);
                    FileData fd = new FileData();
                    fd.filename = extractStringValue(obj, "filename");
                    fd.additions = extractIntValue(obj, "additions");
                    fd.deletions = extractIntValue(obj, "deletions");
                    if (fd.filename != null) files.add(fd);
                    start = i + 2; // skip comma
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse changed files JSON: {}", e.getMessage());
        }
        return files;
    }

    private List<CommitData> parseCommits(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<CommitData> commits = new ArrayList<>();
        try {
            String trimmed = json.trim();
            if (!trimmed.startsWith("[")) return List.of();
            String content = trimmed.substring(1, trimmed.length() - 1);

            int depth = 0;
            int start = 0;
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '{') depth++;
                if (content.charAt(i) == '}') depth--;
                if (depth == 0 && content.charAt(i) == '}') {
                    String obj = content.substring(start, i + 1);
                    CommitData cd = new CommitData();
                    cd.sha = extractStringValue(obj, "sha");
                    cd.message = extractStringValue(obj, "message");
                    if (cd.sha != null) commits.add(cd);
                    start = i + 2;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse commits JSON: {}", e.getMessage());
        }
        return commits;
    }

    private String extractStringValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private int extractIntValue(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        int start = idx + search.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return 0;
        return Integer.parseInt(json.substring(start, end));
    }

    /**
     * Simple file data holder for evaluation.
     */
    private static class FileData {
        String filename;
        int additions;
        int deletions;
    }

    /**
     * Simple commit data holder for evaluation.
     */
    private static class CommitData {
        String sha;
        String message;
    }
}
