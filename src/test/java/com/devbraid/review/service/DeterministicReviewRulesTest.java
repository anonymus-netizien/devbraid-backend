package com.devbraid.review.service;

import com.devbraid.analysis.service.CommitMessageAnalyzer;
import com.devbraid.analysis.service.RiskFlagRules;
import com.devbraid.analysis.service.TestCoverageGapDetector;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import com.devbraid.github.dto.response.GitCommitAuthor;
import com.devbraid.review.entity.FindingCategory;
import com.devbraid.review.entity.FindingSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeterministicReviewRules Unit Tests")
class DeterministicReviewRulesTest {

    private final DeterministicReviewRules rules = new DeterministicReviewRules(
            new RiskFlagRules(),
            new TestCoverageGapDetector(),
            new CommitMessageAnalyzer());

    @Test
    @DisplayName("security-path change produces a SECURITY finding")
    void evaluate_securityPath_mapsToSecurityCategory() {
        List<ChangedFileDto> files = List.of(new ChangedFileDto(
                "src/main/java/com/app/security/AuthService.java", "modified", 5, 1, "@@ -1 +1 @@\n+x"));

        List<ReviewFinding> findings = rules.evaluate(List.of(), files);

        assertThat(findings).anySatisfy(f -> {
            assertThat(f.severity()).isEqualTo(FindingSeverity.HIGH);
            assertThat(f.category()).isEqualTo(FindingCategory.SECURITY);
            assertThat(f.title()).isEqualTo("securityPaths");
        });
    }

    @Test
    @DisplayName("untested high-risk production file produces a TESTING finding")
    void evaluate_untestedFile_producesTestingFinding() {
        List<ChangedFileDto> files = List.of(new ChangedFileDto(
                "src/main/java/com/app/service/OrderService.java", "modified", 30, 0, "@@ -1 +1 @@\n+x"));

        List<ReviewFinding> findings = rules.evaluate(
                List.of(new CommitSummaryDto("sha1", "feat: add order logic", new GitCommitAuthor("a", "a@b.c"))),
                files);

        assertThat(findings).anySatisfy(f -> {
            assertThat(f.category()).isEqualTo(FindingCategory.TESTING);
            assertThat(f.filePath()).contains("OrderService");
        });
    }

    @Test
    @DisplayName("breaking-change commit message produces a DOCUMENTATION finding")
    void evaluate_breakingChange_producesDocumentationFinding() {
        List<CommitSummaryDto> commits = List.of(new CommitSummaryDto("sha1",
                "feat!: remove deprecated API", new GitCommitAuthor("a", "a@b.c")));

        List<ReviewFinding> findings = rules.evaluate(commits, List.of());

        assertThat(findings).anySatisfy(f -> {
            assertThat(f.category()).isEqualTo(FindingCategory.DOCUMENTATION);
            assertThat(f.title()).isEqualTo("Breaking change");
        });
    }
}
