package com.devbraid.review.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import com.devbraid.review.entity.FindingSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("AiReviewGenerator Unit Tests")
@ExtendWith(MockitoExtension.class)
class AiReviewGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ChangedFileDto> files = List.of(new ChangedFileDto(
            "src/main/java/App.java", "modified", 2, 0,
            "@@ -0,0 +1,2 @@\n+line1\n+line2"));
    private final List<CommitSummaryDto> commits = List.of();
    @Mock
    private AIProvider aiProvider;
    private AiReviewGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AiReviewGenerator(aiProvider, objectMapper);
    }

    @Test
    @DisplayName("generate() parses and validates a valid AI response")
    void generate_validResponse_parsesFindings() throws Exception {
        String response = """
                {"summary":"Looks good overall","findings":[
                {"severity":"HIGH","category":"BUG","file":"src/main/java/App.java","line":2,"title":"Null deref","body":"line2 can be null"}]}""";
        when(aiProvider.analyze(anyString())).thenReturn(response);

        AiReviewResult result = generator.generate(files, commits);

        assertThat(result.summary()).isEqualTo("Looks good overall");
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).severity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(result.findings().get(0).lineNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("generate() tolerates markdown code fences around the JSON")
    void generate_fencedJson_parsesFindings() throws Exception {
        when(aiProvider.analyze(anyString())).thenReturn("```json\n{\"summary\":\"s\",\"findings\":[]}\n```");

        AiReviewResult result = generator.generate(files, commits);

        assertThat(result.summary()).isEqualTo("s");
        assertThat(result.findings()).isEmpty();
    }

    @Test
    @DisplayName("generate() drops findings on lines outside the diff")
    void generate_offDiffLine_dropped() throws Exception {
        String response = """
                {"summary":"s","findings":[
                {"severity":"HIGH","category":"BUG","file":"src/main/java/App.java","line":99,"title":"X","body":"off diff"}]}""";
        when(aiProvider.analyze(anyString())).thenReturn(response);

        AiReviewResult result = generator.generate(files, commits);

        assertThat(result.findings()).isEmpty();
    }

    @Test
    @DisplayName("generate() drops findings for unknown files and bad enums")
    void generate_invalidFinding_dropped() throws Exception {
        String response = """
                {"summary":"s","findings":[
                {"severity":"BOGUS","category":"BUG","file":"src/main/java/App.java","line":1,"title":"X","body":"bad severity"},
                {"severity":"HIGH","category":"BUG","file":"missing/File.java","line":1,"title":"X","body":"unknown file"}]}""";
        when(aiProvider.analyze(anyString())).thenReturn(response);

        AiReviewResult result = generator.generate(files, commits);

        assertThat(result.findings()).isEmpty();
    }

    @Test
    @DisplayName("generate() throws on malformed JSON — the fallback aspect converts it")
    void generate_malformedJson_throws() throws Exception {
        when(aiProvider.analyze(anyString())).thenReturn("not json at all");

        assertThatThrownBy(() -> generator.generate(files, commits))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("generate() returns null when no AI key is configured (provider throws)")
    void generate_providerThrows_propagatesForFallback() throws Exception {
        when(aiProvider.analyze(anyString())).thenThrow(new IllegalStateException("AI provider API key not configured"));

        assertThatThrownBy(() -> generator.generate(files, commits))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("noAiReview() returns null — deterministic-only fallback")
    void noAiReview_returnsNull() {
        assertThat(generator.noAiReview(files, commits)).isNull();
    }
}
