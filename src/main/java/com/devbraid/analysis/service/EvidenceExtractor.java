package com.devbraid.analysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts structured evidence from commits and changed files JSON
 * for use in risk analysis and brief generation.
 */
@Slf4j
@Component
public class EvidenceExtractor {

    /**
     * Extract evidence summary from commits and changed files.
     *
     * @param commitsJson      serialized commits JSON
     * @param changedFilesJson serialized changed files JSON
     * @return map of evidence categories to their values
     */
    public Map<String, Object> extract(String commitsJson, String changedFilesJson) {
        Map<String, Object> evidence = new HashMap<>();

        List<String> filenames = extractFilenames(changedFilesJson);
        int totalAdditions = extractSumField(changedFilesJson, "additions");
        int totalDeletions = extractSumField(changedFilesJson, "deletions");

        evidence.put("fileCount", filenames.size());
        evidence.put("totalAdditions", totalAdditions);
        evidence.put("totalDeletions", totalDeletions);
        evidence.put("totalLinesChanged", totalAdditions + totalDeletions);
        evidence.put("securityFiles", extractMatching(filenames, "/security/", "/auth/", "/crypto/"));
        evidence.put("testFiles", extractMatching(filenames, "Test.java", "Test.ts", ".test.", "/test/", "/__tests__/"));
        evidence.put("configFiles", extractMatching(filenames, "/config/", "application", ".yml"));
        evidence.put("migrationFiles", extractMatching(filenames, "V", "__", ".sql"));
        evidence.put("dependencyFiles", extractMatching(filenames, "pom.xml", "package.json", "package-lock.json", "yarn.lock"));
        evidence.put("filenames", filenames);

        return evidence;
    }

    // ── Private helpers ──────────────────────────────────────────────

    private List<String> extractFilenames(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<String> filenames = new ArrayList<>();
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
                    String filename = extractStringValue(obj, "filename");
                    if (filename != null) filenames.add(filename);
                    start = i + 2;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract filenames: {}", e.getMessage());
        }
        return filenames;
    }

    private int extractSumField(String json, String field) {
        if (json == null || json.isBlank()) return 0;
        int sum = 0;
        try {
            String trimmed = json.trim();
            if (!trimmed.startsWith("[")) return 0;
            String content = trimmed.substring(1, trimmed.length() - 1);

            int depth = 0;
            int start = 0;
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '{') depth++;
                if (content.charAt(i) == '}') depth--;
                if (depth == 0 && content.charAt(i) == '}') {
                    String obj = content.substring(start, i + 1);
                    sum += extractIntValue(obj, field);
                    start = i + 2;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to sum field {}: {}", field, e.getMessage());
        }
        return sum;
    }

    private List<String> extractMatching(List<String> filenames, String... patterns) {
        return filenames.stream()
                .filter(f -> {
                    for (String pattern : patterns) {
                        if (f.contains(pattern)) return true;
                    }
                    return false;
                })
                .toList();
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
}
