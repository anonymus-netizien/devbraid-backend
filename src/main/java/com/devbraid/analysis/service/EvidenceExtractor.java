package com.devbraid.analysis.service;

import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Extracts structured evidence from typed changed-file DTOs — no JSON parsing.
 */
@Slf4j
@Component
public class EvidenceExtractor {

    public Map<String, Object> extract(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles) {
        Map<String, Object> evidence = new HashMap<>();

        List<ChangedFileDto> files = changedFiles != null ? changedFiles : List.of();

        List<String> filenames = files.stream()
                .map(ChangedFileDto::getFilename)
                .filter(Objects::nonNull)
                .toList();

        int totalAdditions = files.stream().mapToInt(ChangedFileDto::getAdditions).sum();
        int totalDeletions = files.stream().mapToInt(ChangedFileDto::getDeletions).sum();

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
