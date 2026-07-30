package com.devbraid.analysis.service;

import com.devbraid.analysis.util.JsonParseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts structured evidence from commits and changed files JSON.
 * Uses shared JsonParseUtils for JSON parsing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceExtractor {

    private final JsonParseUtils jsonParseUtils;

    public Map<String, Object> extract(String commitsJson, String changedFilesJson) {
        Map<String, Object> evidence = new HashMap<>();

        List<Map<String, Object>> files = jsonParseUtils.parseArray(changedFilesJson);

        List<String> filenames = files.stream()
                .map(f -> jsonParseUtils.getString(f, "filename"))
                .filter(java.util.Objects::nonNull)
                .toList();

        int totalAdditions = files.stream().mapToInt(f -> jsonParseUtils.getInt(f, "additions")).sum();
        int totalDeletions = files.stream().mapToInt(f -> jsonParseUtils.getInt(f, "deletions")).sum();

        evidence.put("fileCount", filenames.size());
        evidence.put("totalAdditions", totalAdditions);
        evidence.put("totalDeletions", totalDeletions);
        evidence.put("totalLinesChanged", totalAdditions + totalDeletions);
        evidence.put("securityFiles", match(filenames, "/security/", "/auth/", "/crypto/"));
        evidence.put("testFiles", match(filenames, "Test.java", "Test.ts", ".test.", "/test/", "/__tests__/"));
        evidence.put("configFiles", match(filenames, "/config/", "application"));
        evidence.put("migrationFiles", match(filenames, ".sql"));
        evidence.put("dependencyFiles", match(filenames, "pom.xml", "package.json", "package-lock.json", "yarn.lock"));
        evidence.put("filenames", filenames);

        return evidence;
    }

    private List<String> match(List<String> filenames, String... patterns) {
        return filenames.stream()
                .filter(f -> {
                    for (String p : patterns) if (f.contains(p)) return true;
                    return false;
                })
                .toList();
    }
}
