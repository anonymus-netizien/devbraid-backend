package com.devbraid.analysis.service;

import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Suggests decision notes based on diff patterns and commit analysis.
 * Helps developers capture "why" by detecting common change patterns
 * and generating starter notes they can confirm or edit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDecisionNotes {

    private static final int LARGE_CHANGE_THRESHOLD = 100;
    // Pattern → suggested note template
    private static final Map<Pattern, String> PATTERN_NOTES = new LinkedHashMap<>();

    static {
        PATTERN_NOTES.put(
                Pattern.compile("(?i)(security|auth|token|encrypt|csrf|xss)"),
                "Security consideration: This change involves authentication/security logic. Verify no credential leaks, proper token validation, and OWASP compliance."
        );
        PATTERN_NOTES.put(
                Pattern.compile("(?i)(migration|flyway|schema|ALTER|CREATE TABLE|DROP|V\\d+__.*\\.sql)"),
                "Database migration detected: Ensure backward compatibility, verify rollback strategy, and check for data loss risks."
        );
        PATTERN_NOTES.put(
                Pattern.compile("(?i)(config|properties|yml|env)"),
                "Configuration change: Verify environment-specific values, check for hardcoded secrets, and ensure all environments are covered."
        );
        PATTERN_NOTES.put(
                Pattern.compile("(?i)(api|endpoint|controller|REST|GraphQL)"),
                "API change: Verify backward compatibility, check request/response contracts, and ensure proper error handling."
        );
        PATTERN_NOTES.put(
                Pattern.compile("(?i)(exception|error|throw|catch)"),
                "Error handling change: Verify exception propagation, ensure proper HTTP status codes, and check for swallowed exceptions."
        );
        PATTERN_NOTES.put(
                Pattern.compile("(?i)(cache|redis|memcache|invalidate)"),
                "Caching change: Verify cache invalidation strategy, check for stale data risks, and ensure TTL settings are appropriate."
        );
        PATTERN_NOTES.put(
                Pattern.compile("(?i)(async|thread|concurrent|parallel|CompletableFuture)"),
                "Concurrency change: Verify thread safety, check for race conditions, and ensure proper synchronization."
        );
        PATTERN_NOTES.put(
                Pattern.compile("(?i)(delete|remove|drop|purge)"),
                "Deletion detected: Verify soft vs hard delete, check for cascade effects, and ensure data retention compliance."
        );
    }

    /**
     * Generate suggested decision notes from typed changed files and commits — no JSON parsing.
     */
    public List<SuggestedNote> suggestNotes(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles) {
        List<SuggestedNote> suggestions = new ArrayList<>();
        Set<String> seenPatterns = new HashSet<>();

        // Analyze file paths for patterns
        if (changedFiles != null && !changedFiles.isEmpty()) {
            for (ChangedFileDto file : changedFiles) {
                String filename = file.getFilename();
                if (filename == null) continue;

                int additions = file.getAdditions();
                int deletions = file.getDeletions();

                for (Map.Entry<Pattern, String> entry : PATTERN_NOTES.entrySet()) {
                    String key = entry.getKey().pattern();
                    if (entry.getKey().matcher(filename).find() && !seenPatterns.contains(key)) {
                        seenPatterns.add(key);
                        suggestions.add(new SuggestedNote(
                                categorizeNote(filename),
                                entry.getValue(),
                                SuggestionSource.FILE_ANALYSIS,
                                filename,
                                additions + deletions
                        ));
                    }
                }

                // Large file changes
                if (additions + deletions > LARGE_CHANGE_THRESHOLD && !seenPatterns.contains("largeChange:" + filename)) {
                    seenPatterns.add("largeChange:" + filename);
                    suggestions.add(new SuggestedNote(
                            "large-change",
                            String.format("Large change in %s (+%d/-%d lines). Consider splitting into smaller, focused changes for easier review.", filename, additions, deletions),
                            SuggestionSource.FILE_ANALYSIS,
                            filename,
                            additions + deletions
                    ));
                }
            }
        }

        // Analyze commit messages
        if (commits != null && !commits.isEmpty()) {
            analyzeCommitsForNotes(commits, seenPatterns, suggestions);
        }

        return suggestions;
    }

    private void analyzeCommitsForNotes(List<CommitSummaryDto> commits, Set<String> seen, List<SuggestedNote> suggestions) {
        for (CommitSummaryDto commit : commits) {
            String message = commit.getMessage();
            if (message == null) continue;

            for (Map.Entry<Pattern, String> entry : PATTERN_NOTES.entrySet()) {
                String key = entry.getKey().pattern();
                if (entry.getKey().matcher(message).find() && !seen.contains(key)) {
                    seen.add(key);
                    String firstLine = message.contains("\n") ? message.substring(0, message.indexOf('\n')).trim() : message.trim();
                    suggestions.add(new SuggestedNote(
                            categorizeNote(message),
                            entry.getValue(),
                            SuggestionSource.COMMIT_ANALYSIS,
                            firstLine,
                            0
                    ));
                }
            }
        }
    }

    /**
     * Categorize a note based on context string (filename or commit message).
     * Checks both filename extensions and keyword presence.
     */
    private String categorizeNote(String context) {
        if (context == null) return "general";
        String lower = context.toLowerCase();

        // Check filename extensions first (before keyword matching)
        if (lower.endsWith(".sql")) return "database";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")) return "configuration";

        // Keyword-based categorization
        if (lower.contains("security") || lower.contains("auth") || lower.contains("xss") || lower.contains("csrf"))
            return "security";
        if (lower.contains("migration") || lower.contains("schema") || lower.contains("flyway") || lower.contains("database"))
            return "database";
        if (lower.contains("config") || lower.contains("properties") || lower.contains("env")) return "configuration";
        if (lower.contains("api") || lower.contains("controller") || lower.contains("endpoint")) return "api";
        if (lower.contains("test") || lower.contains("spec")) return "testing";
        return "general";
    }

    // ── Result DTOs ─────────────────────────────────────────────────

    public enum SuggestionSource {
        FILE_ANALYSIS,
        COMMIT_ANALYSIS,
        PATTERN_MATCH
    }

    public record SuggestedNote(
            String category,
            String content,
            SuggestionSource source,
            String sourceFile,
            int affectedLines
    ) {
    }
}
