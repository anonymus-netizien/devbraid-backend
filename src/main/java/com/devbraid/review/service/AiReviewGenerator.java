package com.devbraid.review.service;

import com.devbraid.ai.fallback.FallbackMethod;
import com.devbraid.ai.service.AIProvider;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import com.devbraid.review.entity.FindingCategory;
import com.devbraid.review.entity.FindingSeverity;
import com.devbraid.review.util.DiffHunkParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AI phase of the PR review: sends a bounded diff context to the LLM and parses
 * the structured findings. Any failure (AI unavailable, malformed/truncated
 * output, findings outside the diff) falls back via {@code @FallbackMethod} to
 * {@link #noAiReview} — the caller keeps the deterministic-only review.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewGenerator {

    private static final int CONTEXT_BUDGET_CHARS = 25_000;
    private static final int MAX_FINDINGS = 20;

    private final AIProvider aiProvider;
    private final ObjectMapper objectMapper;

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @FallbackMethod(method = "noAiReview")
    public AiReviewResult generate(List<ChangedFileDto> files, List<CommitSummaryDto> commits) throws Exception {
        String diff = buildDiffContext(files);
        if (diff.isBlank()) {
            return null;
        }
        String response = aiProvider.analyze(buildPrompt(diff));
        return parse(response, files);
    }

    /**
     * Fallback: AI unavailable or output invalid → null, deterministic-only review.
     */
    public AiReviewResult noAiReview(List<ChangedFileDto> files, List<CommitSummaryDto> commits) {
        return null;
    }

    private String buildDiffContext(List<ChangedFileDto> files) {
        StringBuilder sb = new StringBuilder();
        for (ChangedFileDto file : files) {
            String patch = file.getPatch();
            if (patch == null || patch.isBlank()) {
                continue;
            }
            String header = "--- " + file.getFilename()
                    + " (" + file.getStatus() + ", +" + file.getAdditions() + "/-" + file.getDeletions() + ")\n";
            if (sb.length() + header.length() + patch.length() > CONTEXT_BUDGET_CHARS) {
                int remaining = CONTEXT_BUDGET_CHARS - sb.length();
                if (remaining > header.length() + 64) {
                    sb.append(header).append(patch, 0, remaining - header.length()).append("\n[...truncated...]");
                }
                break;
            }
            sb.append(header).append(patch).append("\n");
        }
        return sb.toString();
    }

    private String buildPrompt(String diff) {
        return """
                You are DevBraid's automated PR reviewer (similar to Code Rabbit). Review the pull request diff below.
                
                Rules:
                - Report only real, actionable issues. Do not invent problems.
                - Every finding must reference an ADDED or MODIFIED line in the diff. The 'line' must be the NEW file line number of an added line.
                - Keep titles under 8 words and bodies under 40 words.
                - Maximum 8 findings.
                
                Return ONLY a JSON object (no markdown, no code fences) with this exact schema:
                {"summary": "<2-4 sentence review summary>", "findings": [{"severity": "CRITICAL|HIGH|MEDIUM|LOW|INFO", "category": "BUG|SECURITY|PERFORMANCE|CORRECTNESS|TESTING|STYLE|MAINTAINABILITY|DOCUMENTATION|OTHER", "file": "<filename from diff>", "line": <new line number>, "title": "<short title>", "body": "<concise explanation>"}]}
                
                Diff:
                %s
                """.formatted(diff);
    }

    /**
     * Parse and strictly validate the AI response. Invalid JSON, unknown enums,
     * unknown files, or lines outside the diff throw IllegalArgumentException —
     * the {@code @FallbackMethod} aspect converts that into a deterministic-only review.
     */
    private AiReviewResult parse(String response, List<ChangedFileDto> files) throws Exception {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("AI review response empty");
        }
        String json = response.substring(response.indexOf('{'), response.lastIndexOf('}') + 1);
        JsonNode root = objectMapper.readTree(json);

        String summary = root.path("summary").isTextual() ? root.path("summary").asText() : null;

        Map<String, Set<Integer>> changedLines = new HashMap<>();
        for (ChangedFileDto file : files) {
            if (file.getPatch() != null && !file.getPatch().isBlank()) {
                changedLines.put(file.getFilename(),
                        DiffHunkParser.changedNewLines(DiffHunkParser.parseHunks(file.getPatch())));
            }
        }

        List<ReviewFinding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonNode array = root.path("findings");
        if (array.isArray()) {
            for (JsonNode node : array) {
                ReviewFinding finding = toFinding(node, changedLines);
                if (finding != null
                        && seen.add(finding.filePath() + ":" + finding.lineNumber() + ":" + finding.title())) {
                    findings.add(finding);
                    if (findings.size() >= MAX_FINDINGS) {
                        break;
                    }
                }
            }
        }
        return new AiReviewResult(summary, findings);
    }

    private ReviewFinding toFinding(JsonNode node, Map<String, Set<Integer>> changedLines) {
        FindingSeverity severity = enumOrNull(FindingSeverity.class, node.path("severity").asText());
        FindingCategory category = enumOrNull(FindingCategory.class, node.path("category").asText());
        String file = node.path("file").asText("");
        String title = node.path("title").asText("");
        String body = node.path("body").asText("");
        if (severity == null || category == null || file.isBlank() || title.isBlank() || body.isBlank()
                || !node.path("line").isInt()) {
            return null;
        }
        int line = node.path("line").asInt();
        Set<Integer> lines = changedLines.get(file);
        if (lines == null || !lines.contains(line)) {
            return null;
        }
        return new ReviewFinding(severity, category, file, line, title, body);
    }
}
