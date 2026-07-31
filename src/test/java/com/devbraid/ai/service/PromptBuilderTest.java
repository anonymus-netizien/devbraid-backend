package com.devbraid.ai.service;

import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.ThreadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptBuilder Unit Tests")
class PromptBuilderTest {

    private PromptBuilder promptBuilder;
    private ChangeThread testThread;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
        testThread = ChangeThread.builder()
                .id(UUID.randomUUID())
                .title("Test Thread")
                .repositoryFullName("owner/repo")
                .headBranch("feature")
                .baseBranch("main")
                .status(ThreadStatus.DRAFT)
                .commits("[{\"sha\":\"abc123\",\"message\":\"feat: add auth\"}]")
                .changedFiles("[{\"filename\":\"src/auth/AuthService.java\"}]")
                .build();
    }

    @Test
    @DisplayName("buildBriefPrompt() enforces the citation/inference rule — the product differentiator")
    void buildBriefPrompt_containsCitationRule() {
        String prompt = promptBuilder.buildBriefPrompt(testThread);

        assertThat(prompt)
                .contains("CITATION RULE")
                .contains("[file:")
                .contains("[commit:")
                .contains("[source:")
                .contains("[inference]")
                .contains("Output is rejected");
    }

    @Test
    @DisplayName("buildBriefPrompt() includes thread context for grounding citations")
    void buildBriefPrompt_includesThreadContext() {
        String prompt = promptBuilder.buildBriefPrompt(testThread);

        assertThat(prompt)
                .contains("Test Thread")
                .contains("owner/repo")
                .contains("feature")
                .contains("main")
                .contains("abc123")
                .contains("src/auth/AuthService.java");
    }

    @Test
    @DisplayName("buildTemplateBrief() clearly marks itself as fallback output")
    void buildTemplateBrief_marksAsFallback() {
        String template = promptBuilder.buildTemplateBrief(testThread);

        assertThat(template)
                .contains("Change Brief: Test Thread")
                .contains("template fallback")
                .contains("OPENAI_API_KEY");
    }
}
