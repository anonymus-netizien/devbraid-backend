package com.devbraid.ai.service;

import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds structured prompts for AI analysis and brief generation.
 * Centralizes prompt construction so callers don't inline prompt formatting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final ObjectMapper objectMapper;

    /**
     * Build a prompt for AI-powered brief generation from thread data.
     */
    public String buildBriefPrompt(ChangeThread thread) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a structured Change Brief for this code change.\n\n");
        prompt.append("## Thread: ").append(thread.getTitle()).append("\n");
        prompt.append("Repository: ").append(thread.getRepositoryFullName()).append("\n");
        prompt.append("Branch: ").append(thread.getHeadBranch()).append(" → ").append(thread.getBaseBranch()).append("\n\n");

        appendJson(prompt, "### Commits", thread.getCommits());
        appendJson(prompt, "### Changed Files", thread.getChangedFiles());
        appendJson(prompt, "### Risk Assessment", thread.getRiskReport());

        prompt.append("Generate a Markdown brief with:\n");
        prompt.append("1. **Summary** — What changed and why\n");
        prompt.append("2. **Key Changes** — List of important modifications\n");
        prompt.append("3. **Risk Assessment** — Potential risks and mitigations\n");
        prompt.append("4. **Testing Recommendations** — What to test\n\n");
        prompt.append("CITATION RULE (mandatory): Every factual claim MUST be immediately followed by a citation ");
        prompt.append("marker in one of these forms: [file: <path>], [commit: <sha>], or [source: <reference>]. ");
        prompt.append("If a statement is your own reasoning or a guess, mark it [inference] instead. ");
        prompt.append("Output is rejected if it contains claims with neither a citation marker nor an [inference] marker.\n");

        return prompt.toString();
    }

    /**
     * Build a template brief (fallback when AI is unavailable).
     */
    public String buildTemplateBrief(ChangeThread thread) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Change Brief: ").append(thread.getTitle()).append("\n\n");
        sb.append("**Repository:** ").append(thread.getRepositoryFullName()).append("\n");
        sb.append("**Branch:** ").append(thread.getHeadBranch()).append(" → ").append(thread.getBaseBranch()).append("\n\n");
        sb.append("## Summary\n\n");
        sb.append("This change encompasses modifications across ").append(thread.getRepositoryFullName()).append(" ");
        sb.append("from branch ").append(thread.getHeadBranch()).append(" into ").append(thread.getBaseBranch()).append(".\n\n");

        if (thread.getRiskLevel() != null) {
            sb.append("## Risk Assessment\n\n");
            sb.append("**Overall Risk:** ").append(thread.getRiskLevel()).append("\n\n");
        }

        appendJson(sb, "### Risk Flags", thread.getRiskReport());

        sb.append("---\n\n");
        sb.append("*This brief was generated using template fallback (AI service unavailable).\n");
        sb.append("Configure OPENAI_API_KEY for AI-enhanced briefs.*\n");

        return sb.toString();
    }

    /**
     * Build a prompt for AI-powered risk analysis from typed commits and changed files.
     */
    public String buildAnalysisPrompt(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles,
                                      java.util.List<com.devbraid.analysis.dto.RiskFlagDto> flags) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze these code changes and provide a structured risk assessment.\n\n");
        appendJson(prompt, "Changed files", changedFiles);
        appendJson(prompt, "Commits", commits);
        prompt.append("Pre-computed risk flags:\n");
        for (com.devbraid.analysis.dto.RiskFlagDto flag : flags) {
            prompt.append("- ").append(flag.getRule()).append(": ").append(flag.getMessage()).append("\n");
        }
        prompt.append("\nProvide: 1) Risk summary 2) Key concerns 3) Recommendations");
        return prompt.toString();
    }

    /**
     * Build a prompt for AI-generated decision note suggestions.
     * Sprint 5: enriches diff context with commit analysis for better note generation.
     */
    public String buildDecisionNotePrompt(List<CommitSummaryDto> commits, List<ChangedFileDto> changedFiles,
                                          java.util.List<String> riskIndicators) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Based on the following code changes, suggest 2-3 decision notes that capture the 'why' behind these changes.\n\n");
        appendJson(prompt, "Changed files", changedFiles);
        appendJson(prompt, "Commits", commits);

        if (riskIndicators != null && !riskIndicators.isEmpty()) {
            prompt.append("Risk indicators:\n");
            for (String indicator : riskIndicators) {
                prompt.append("- ").append(indicator).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("For each note, provide:\n");
        prompt.append("1. Category (security, architecture, performance, testing, general)\n");
        prompt.append("2. A concise note explaining the reasoning behind the change\n");
        prompt.append("3. Priority (high, medium, low)\n");

        return prompt.toString();
    }

    /**
     * Serialize a typed value to JSON for prompt context.
     * ponytail: prompt-building helper — if serialization ever fails, degrade to toString.
     */
    private void appendJson(StringBuilder sb, String heading, Object value) {
        if (value == null) return;
        sb.append(heading).append("\n");
        try {
            sb.append(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            sb.append(value);
        }
        sb.append("\n\n");
    }
}
