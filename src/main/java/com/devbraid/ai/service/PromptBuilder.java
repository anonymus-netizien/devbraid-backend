package com.devbraid.ai.service;

import com.devbraid.changethread.entity.ChangeThread;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds structured prompts for AI analysis and brief generation.
 * Centralizes prompt construction so callers don't inline prompt formatting.
 */
@Slf4j
@Component
public class PromptBuilder {

    /**
     * Build a prompt for AI-powered brief generation from thread data.
     */
    public String buildBriefPrompt(ChangeThread thread) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a structured Change Brief for this code change.\n\n");
        prompt.append("## Thread: ").append(thread.getTitle()).append("\n");
        prompt.append("Repository: ").append(thread.getRepositoryFullName()).append("\n");
        prompt.append("Branch: ").append(thread.getHeadBranch()).append(" → ").append(thread.getBaseBranch()).append("\n\n");

        if (thread.getCommits() != null) {
            prompt.append("### Commits\n").append(thread.getCommits()).append("\n\n");
        }
        if (thread.getChangedFiles() != null) {
            prompt.append("### Changed Files\n").append(thread.getChangedFiles()).append("\n\n");
        }
        if (thread.getRiskReport() != null) {
            prompt.append("### Risk Assessment\n").append(thread.getRiskReport()).append("\n\n");
        }

        prompt.append("Generate a Markdown brief with:\n");
        prompt.append("1. **Summary** — What changed and why\n");
        prompt.append("2. **Key Changes** — List of important modifications\n");
        prompt.append("3. **Risk Assessment** — Potential risks and mitigations\n");
        prompt.append("4. **Testing Recommendations** — What to test\n");

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

        if (thread.getRiskReport() != null) {
            sb.append("### Risk Flags\n\n");
            sb.append(thread.getRiskReport()).append("\n\n");
        }

        sb.append("---\n\n");
        sb.append("*This brief was generated using template fallback (AI service unavailable).\n");
        sb.append("Configure OPENAI_API_KEY for AI-enhanced briefs.*\n");

        return sb.toString();
    }

    /**
     * Build a prompt for AI-powered risk analysis from commits and changed files.
     */
    public String buildAnalysisPrompt(String commitsJson, String changedFilesJson, java.util.List<com.devbraid.analysis.dto.RiskFlagDto> flags) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze these code changes and provide a structured risk assessment.\n\n");
        prompt.append("Changed files:\n").append(changedFilesJson != null ? changedFilesJson : "N/A").append("\n\n");
        prompt.append("Commits:\n").append(commitsJson != null ? commitsJson : "N/A").append("\n\n");
        prompt.append("Pre-computed risk flags:\n");
        for (com.devbraid.analysis.dto.RiskFlagDto flag : flags) {
            prompt.append("- ").append(flag.getRule()).append(": ").append(flag.getMessage()).append("\n");
        }
        prompt.append("\nProvide: 1) Risk summary 2) Key concerns 3) Recommendations");
        return prompt.toString();
    }
}
