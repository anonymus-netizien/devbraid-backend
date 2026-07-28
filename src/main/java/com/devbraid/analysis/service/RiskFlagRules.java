package com.devbraid.analysis.service;

import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.changethread.entity.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic risk evaluation rules for code changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskFlagRules {

    private final JsonArrayParser jsonParser;

    private static final int LARGE_DIFF_THRESHOLD = 500;
    private static final int MANY_FILES_THRESHOLD = 20;

    public List<RiskFlagDto> evaluate(String commitsJson, String changedFilesJson) {
        List<RiskFlagDto> flags = new ArrayList<>();

        List<FileData> files = jsonParser.parseArray(changedFilesJson, this::mapFile);
        List<CommitData> commits = jsonParser.parseArray(commitsJson, this::mapCommit);

        int totalLinesChanged = files.stream().mapToInt(f -> f.additions + f.deletions).sum();

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
                .filter(f -> f.filename.contains("/security/") || f.filename.contains("/auth/") || f.filename.contains("/crypto/"))
                .map(f -> f.filename).toList();
        if (!securityFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("securityPaths").severity(RiskLevel.HIGH)
                    .message("Security-related changes detected")
                    .evidence(securityFiles).build());
        }

        boolean hasTestFiles = files.stream()
                .anyMatch(f -> f.filename.endsWith("Test.java") || f.filename.endsWith("Test.ts")
                        || f.filename.endsWith(".test.java") || f.filename.endsWith(".test.ts")
                        || f.filename.contains("/test/") || f.filename.contains("/__tests__/"));
        if (!hasTestFiles && !files.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("noTests").severity(RiskLevel.LOW)
                    .message("No test files changed")
                    .evidence(List.of("Consider adding tests for these changes"))
                    .build());
        }

        List<String> configFiles = files.stream()
                .filter(f -> f.filename.contains("/config/") || (f.filename.contains("application") && f.filename.endsWith(".yml")))
                .map(f -> f.filename).toList();
        if (!configFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("configChanges").severity(RiskLevel.LOW)
                    .message("Configuration files changed")
                    .evidence(configFiles).build());
        }

        List<String> migrationFiles = files.stream()
                .filter(f -> f.filename.matches(".*V\\d+__.*\\.sql"))
                .map(f -> f.filename).toList();
        if (!migrationFiles.isEmpty()) {
            flags.add(RiskFlagDto.builder()
                    .rule("migrationFiles").severity(RiskLevel.MEDIUM)
                    .message("Database migration detected")
                    .evidence(migrationFiles).build());
        }

        List<String> dependencyFiles = files.stream()
                .filter(f -> f.filename.equals("pom.xml") || f.filename.equals("package.json")
                        || f.filename.equals("package-lock.json") || f.filename.equals("yarn.lock"))
                .map(f -> f.filename).toList();
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

    private FileData mapFile(String json) {
        FileData fd = new FileData();
        fd.filename = jsonParser.extractStringValue(json, "filename");
        fd.additions = jsonParser.extractIntValue(json, "additions");
        fd.deletions = jsonParser.extractIntValue(json, "deletions");
        return fd.filename != null ? fd : null;
    }

    private CommitData mapCommit(String json) {
        CommitData cd = new CommitData();
        cd.sha = jsonParser.extractStringValue(json, "sha");
        cd.message = jsonParser.extractStringValue(json, "message");
        return cd.sha != null ? cd : null;
    }

    private static class FileData {
        String filename;
        int additions;
        int deletions;
    }

    private static class CommitData {
        String sha;
        String message;
    }
}
