package com.devbraid.analysis.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@DisplayName("AiRiskAnalysis Unit Tests")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisTest {

    private final List<CommitSummaryDto> commits = List.of();
    private final List<ChangedFileDto> changedFiles = List.of();
    private final List<RiskFlagDto> flags = List.of();
    @InjectMocks
    private AiRiskAnalysis aiRiskAnalysis;
    @Mock
    private AIProvider aiProvider;
    @Mock
    private PromptBuilder promptBuilder;

    @Test
    @DisplayName("analyze() returns AI output when the provider succeeds")
    void analyze_returnsAiOutput() throws Exception {
        when(promptBuilder.buildAnalysisPrompt(anyList(), anyList(), any())).thenReturn("prompt");
        when(aiProvider.analyze("prompt")).thenReturn("AI risk summary");

        String result = aiRiskAnalysis.analyze(commits, changedFiles, flags);

        assertThat(result).isEqualTo("AI risk summary");
    }

    @Test
    @DisplayName("analyze() returns null when the provider fails — deterministic-only fallback")
    void analyze_returnsNullOnFailure() throws Exception {
        when(promptBuilder.buildAnalysisPrompt(anyList(), anyList(), any())).thenReturn("prompt");
        when(aiProvider.analyze("prompt")).thenThrow(new RuntimeException("provider down"));

        String result = aiRiskAnalysis.analyze(commits, changedFiles, flags);

        assertThat(result).isNull();
    }
}
