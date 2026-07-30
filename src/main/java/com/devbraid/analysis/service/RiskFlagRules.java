package com.devbraid.analysis.service;

import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.analysis.util.JsonParseUtils;
import com.devbraid.analysis.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic risk evaluation rules for code changes.
 * Uses shared JsonParseUtils for JSON parsing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskFlagRules {

    private static final int LARGE_DIFF_THRESHOLD = 500;
    private static final int MANY_FILES_THRESHOLD = 20;
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

        List<String> dependencyFiles = files.stream()
                .filter(f -> {
                    String name = jsonParseUtils.getString(f, "filename");
                    return name != null && (name.equals("pom.xml") || name.equals("package.json")
                            || name.equals("package-lock.json") || name.equals("yarn.lock"));
                })
                .map(f -> jsonParseUtils.getString(f, "filename"))
                .toList();
        if (!dependencyFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("dependencyChanges").severity(RiskLevel.LOW)
                    .message("Dependency files changed")
                    .evidence(dependencyFiles).build());
        }

        flags.sort((a, b) -> b.getSeverity().compareTo(a.getSeverity()));
        return flags;
    }

    public RiskLevel calculateOverallRisk(List<RiskFlagDto> flags) {
        if (flags == null || flags.isEmpty()) return RiskLevel.NONE;
        return flags.stream().map(RiskFlagDto::getSeverity)
                .max(RiskLevel::compareTo).orElse(RiskLevel.NONE);
    }
}
