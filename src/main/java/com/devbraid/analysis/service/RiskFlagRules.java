package com.devbraid.analysis.service;

import com.devbraid.analysis.RiskLevel;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.analysis.util.JsonParseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic risk evaluation rules for code changes.
 * Uses shared JsonParseUtils for JSON parsing.
 * Enhanced with deeper semantic analysis for Sprint 5.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskFlagRules {

    private static final int LARGE_DIFF_THRESHOLD = 500;
    private static final int MANY_FILES_THRESHOLD = 20;
    private static final int SINGLE_FILE_LARGE_THRESHOLD = 300;
    // File patterns for cross-cutting concern detection
    private static final Set<String> CROSS_CUTTING_PATTERNS = Set.of(
            "SecurityConfig", "WebConfig", "CorsConfig", "CacheConfig",
            "RateLimiter", "Filter", "Interceptor", "Advice"
    );
    // Patterns suggesting tight coupling
    private static final Pattern GOD_CLASS_PATTERN = Pattern.compile(
            "(Service|Manager|Handler|Helper)\\.java$"
    );
    private final JsonParseUtils jsonParseUtils;

    public List<RiskFlagDto> evaluate(String commitsJson, String changedFilesJson) {
        List<RiskFlagDto> flags = new ArrayList<>();

        List<Map<String, Object>> files = jsonParseUtils.parseArray(changedFilesJson);
        List<Map<String, Object>> commits = jsonParseUtils.parseArray(commitsJson);

        int totalLinesChanged = files.stream()
                .mapToInt(f -> jsonParseUtils.getInt(f, "additions") + jsonParseUtils.getInt(f, "deletions"))
                .sum();

        if (totalLinesChanged > LARGE_DIFF_THRESHOLD) {
            flags.add(RiskFlagDto.builder()
                    .rule("largeDiff").severity(RiskLevel.MEDIUM)
                    .message("Large diff: " + totalLinesChanged + " lines changed")
                    .evidence(List.of(totalLinesChanged + " lines across " + files.size() + " files"))
                    .build());
        }

        if (files.size() > MANY_FILES_THRESHOLD) {
            flags.add(RiskFlagDto.builder()
                    .rule("manyFiles").severity(RiskLevel.MEDIUM)
                    .message("Large changeset: " + files.size() + " files")
                    .evidence(List.of(files.size() + " files modified"))
                    .build());
        }

        // ── Sprint 5: Deeper Analysis ──────────────────────────────

        // Single large file changes
        detectSingleFileRisk(files, flags);

        // Cross-cutting concern detection
        detectCrossCuttingConcerns(files, flags);

        // Commit message risk signals
        detectCommitRisks(commits, flags);

        // Dependency version bumps
        detectDependencyRisks(files, flags);

        // Original rules
        detectSecurityPaths(files, flags);
        detectTestGaps(files, flags);
        detectConfigChanges(files, flags);
        detectMigrationFiles(files, flags);

        flags.sort((a, b) -> b.getSeverity().compareTo(a.getSeverity()));
        return flags;
    }

    public RiskLevel calculateOverallRisk(List<RiskFlagDto> flags) {
        if (flags == null || flags.isEmpty()) return RiskLevel.NONE;
        return flags.stream().map(RiskFlagDto::getSeverity)
                .max(RiskLevel::compareTo).orElse(RiskLevel.NONE);
    }

    // ── Enhanced Detection Methods (Sprint 5) ───────────────────────

    private void detectSingleFileRisk(List<Map<String, Object>> files, List<RiskFlagDto> flags) {
        files.stream()
                .filter(f -> {
                    int changes = jsonParseUtils.getInt(f, "additions") + jsonParseUtils.getInt(f, "deletions");
                    return changes > SINGLE_FILE_LARGE_THRESHOLD;
                })
                .forEach(f -> {
                    String name = jsonParseUtils.getString(f, "filename");
                    int additions = jsonParseUtils.getInt(f, "additions");
                    int deletions = jsonParseUtils.getInt(f, "deletions");
                    flags.add(RiskFlagDto.builder()
                            .rule("singleFileLargeDiff").severity(RiskLevel.MEDIUM)
                            .message("Single file has large changes: " + name)
                            .evidence(List.of(name + " +" + additions + "/-" + deletions + " lines"))
                            .build());
                });
    }

    private void detectCrossCuttingConcerns(List<Map<String, Object>> files, List<RiskFlagDto> flags) {
        List<String> crossCutting = files.stream()
                .map(f -> jsonParseUtils.getString(f, "filename"))
                .filter(name -> name != null && CROSS_CUTTING_PATTERNS.stream()
                        .anyMatch(p -> name.contains(p)))
                .toList();

        if (!crossCutting.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("crossCuttingConcern").severity(RiskLevel.HIGH)
                    .message("Cross-cutting concerns modified: " + crossCutting.size() + " file(s)")
                    .evidence(crossCutting)
                    .build());
        }
    }

    private void detectCommitRisks(List<Map<String, Object>> commits, List<RiskFlagDto> flags) {
        long revertCommits = commits.stream()
                .filter(c -> {
                    String msg = jsonParseUtils.getString(c, "message");
                    return msg != null && msg.toLowerCase().startsWith("revert");
                })
                .count();

        if (revertCommits > 0) {
            flags.add(RiskFlagDto.builder()
                    .rule("revertCommits").severity(RiskLevel.HIGH)
                    .message(revertCommits + " revert commit(s) — indicates instability")
                    .evidence(List.of(revertCommits + " revert(s) in this changeset"))
                    .build());
        }

        // Detect WIP/draft commits
        long wipCommits = commits.stream()
                .filter(c -> {
                    String msg = jsonParseUtils.getString(c, "message");
                    return msg != null && (msg.toLowerCase().startsWith("wip") || msg.toLowerCase().contains("work in progress"));
                })
                .count();

        if (wipCommits > 0) {
            flags.add(RiskFlagDto.builder()
                    .rule("wipCommits").severity(RiskLevel.LOW)
                    .message(wipCommits + " WIP commit(s) — may not be ready for review")
                    .evidence(List.of(wipCommits + " WIP commits"))
                    .build());
        }
    }

    private void detectDependencyRisks(List<Map<String, Object>> files, List<RiskFlagDto> flags) {
        boolean pomChanged = files.stream()
                .anyMatch(f -> "pom.xml".equals(jsonParseUtils.getString(f, "filename")));

        boolean packageJsonChanged = files.stream()
                .anyMatch(f -> {
                    String name = jsonParseUtils.getString(f, "filename");
                    return name != null && (name.equals("package.json") || name.equals("package-lock.json") || name.equals("yarn.lock"));
                });

        if (pomChanged) {
            flags.add(RiskFlagDto.builder()
                    .rule("pomDependencyChange").severity(RiskLevel.MEDIUM)
                    .message("pom.xml modified — verify dependency compatibility")
                    .evidence(List.of("pom.xml changed"))
                    .build());
        }

        if (packageJsonChanged) {
            flags.add(RiskFlagDto.builder()
                    .rule("npmDependencyChange").severity(RiskLevel.LOW)
                    .message("npm/yarn dependencies modified")
                    .evidence(List.of("package.json or lockfile changed"))
                    .build());
        }
    }

    // ── Original Detection Methods ──────────────────────────────────

    private void detectSecurityPaths(List<Map<String, Object>> files, List<RiskFlagDto> flags) {
        List<String> securityFiles = files.stream()
                .filter(f -> {
                    String name = jsonParseUtils.getString(f, "filename");
                    return name != null && (name.contains("/security/") || name.contains("/auth/") || name.contains("/crypto/"));
                })
                .map(f -> jsonParseUtils.getString(f, "filename"))
                .toList();
        if (!securityFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("securityPaths").severity(RiskLevel.HIGH)
                    .message("Security-related changes detected")
                    .evidence(securityFiles).build());
        }
    }

    private void detectTestGaps(List<Map<String, Object>> files, List<RiskFlagDto> flags) {
        boolean hasTestFiles = files.stream()
                .anyMatch(f -> {
                    String name = jsonParseUtils.getString(f, "filename");
                    return name != null && (name.endsWith("Test.java") || name.endsWith("Test.ts")
                            || name.endsWith(".test.java") || name.endsWith(".test.ts")
                            || name.contains("/test/") || name.contains("/__tests__/"));
                });
        if (!hasTestFiles && !files.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("noTests").severity(RiskLevel.LOW)
                    .message("No test files changed")
                    .evidence(List.of("Consider adding tests for these changes"))
                    .build());
        }
    }

    private void detectConfigChanges(List<Map<String, Object>> files, List<RiskFlagDto> flags) {
        List<String> configFiles = files.stream()
                .filter(f -> {
                    String name = jsonParseUtils.getString(f, "filename");
                    return name != null && (name.contains("/config/") || (name.contains("application") && name.endsWith(".yml")));
                })
                .map(f -> jsonParseUtils.getString(f, "filename"))
                .toList();
        if (!configFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("configChanges").severity(RiskLevel.LOW)
                    .message("Configuration files changed")
                    .evidence(configFiles).build());
        }
    }

    private void detectMigrationFiles(List<Map<String, Object>> files, List<RiskFlagDto> flags) {
        List<String> migrationFiles = files.stream()
                .filter(f -> {
                    String name = jsonParseUtils.getString(f, "filename");
                    return name != null && name.matches(".*V\\d+__.*\\.sql");
                })
                .map(f -> jsonParseUtils.getString(f, "filename"))
                .toList();
        if (!migrationFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("migrationFiles").severity(RiskLevel.MEDIUM)
                    .message("Database migration detected")
                    .evidence(migrationFiles).build());
        }
    }
}
