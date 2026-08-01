package com.devbraid.brief.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.ThreadStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BriefContentGenerator Unit Tests")
@ExtendWith(MockitoExtension.class)
class BriefContentGeneratorTest {

    @InjectMocks
    private BriefContentGenerator briefContentGenerator;

    @Mock
    private AIProvider aiProvider;

    @Mock
    private PromptBuilder promptBuilder;

    private ChangeThread thread() {
        return ChangeThread.builder()
                .id(UUID.randomUUID())
                .title("Test Thread")
                .repositoryFullName("owner/repo")
                .headBranch("feature")
                .baseBranch("main")
                .status(ThreadStatus.DRAFT)
                .build();
    }

    @Test
    @DisplayName("evidence-backed AI output is returned as-is")
    void generateContent_evidenceBacked_returnsAiOutput() throws Exception {
        when(promptBuilder.buildBriefPrompt(any(ChangeThread.class))).thenReturn("prompt");
        when(aiProvider.analyze("prompt"))
                .thenReturn("**Summary** Added auth [commit:abc123]. **Risk** Low [inference].");

        String content = briefContentGenerator.generateContent(thread());

        assertThat(content).isEqualTo("**Summary** Added auth [commit:abc123]. **Risk** Low [inference].");
        verify(aiProvider).analyze("prompt");
    }

    @Test
    @DisplayName("marker-less AI output falls back to the template")
    void generateContent_withoutMarkers_fallsBackToTemplate() throws Exception {
        when(promptBuilder.buildBriefPrompt(any(ChangeThread.class))).thenReturn("prompt");
        when(aiProvider.analyze("prompt")).thenReturn("Plausible markdown with no markers");
        when(promptBuilder.buildTemplateBrief(any(ChangeThread.class))).thenReturn("Template content");

        String content = briefContentGenerator.generateContent(thread());

        assertThat(content).isEqualTo("Template content");
        verify(promptBuilder).buildTemplateBrief(any(ChangeThread.class));
    }

    @Test
    @DisplayName("buildTemplate returns the template brief")
    void buildTemplate_returnsTemplate() throws Exception {
        when(promptBuilder.buildTemplateBrief(any(ChangeThread.class))).thenReturn("Template content");

        assertThat(briefContentGenerator.buildTemplate(thread())).isEqualTo("Template content");
    }
}
