package com.devbraid.analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts structured evidence from commits and changed files JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceExtractor {

    private final JsonArrayParser jsonParser;

    public Map<String, Object> extract(String commitsJson, String changedFilesJson) {
        Map<String, Object> evidence = new HashMap<>();

        List<String> filenames = jsonParser.parseArray(changedFilesJson,
                json -> jsonParser.extractStringValue(json, "filename"));

        int totalAdditions = sumField(changedFilesJson, "additions");
        int totalDeletions = sumField(changedFilesJson, "deletions");

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

    private int sumField(String json, String field) {
        if (json == null || json.isBlank()) return 0;
        List<Object> items = jsonParser.parseArray(json, obj -> jsonParser.extractIntValue(obj, field));
        return items.stream().mapToInt(i -> (int) i).sum();
    }

    private List<String> match(List<String> filenames, String... patterns) {
        return filenames.stream()
                .filter(f -> { for (String p : patterns) if (f.contains(p)) return true; return false; })
                .toList();
    }
}
