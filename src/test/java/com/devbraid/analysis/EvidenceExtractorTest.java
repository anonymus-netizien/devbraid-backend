package com.devbraid.analysis;

import com.devbraid.analysis.service.EvidenceExtractor;
import com.devbraid.github.dto.response.ChangedFileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EvidenceExtractor — structured evidence extraction.
 * Tests pure extraction logic without Spring context.
 */
@DisplayName("EvidenceExtractor Unit Tests")
class EvidenceExtractorTest {

    private EvidenceExtractor evidenceExtractor;

    private static ChangedFileDto file(String name, int add, int del) {
        return new ChangedFileDto(name, "modified", add, del);
    }

    @BeforeEach
    void setUp() {
        evidenceExtractor = new EvidenceExtractor();
    }

    @Test
    @DisplayName("extract() returns empty evidence for null input")
    void extract_NullInput_ReturnsEmptyEvidence() {
        Map<String, Object> evidence = evidenceExtractor.extract(null, null);

        assertThat(evidence.get("fileCount")).isEqualTo(0);
        assertThat(evidence.get("totalAdditions")).isEqualTo(0);
        assertThat(evidence.get("totalDeletions")).isEqualTo(0);
        assertThat(evidence.get("totalLinesChanged")).isEqualTo(0);
        assertThat((List<?>) evidence.get("filenames")).isEmpty();
    }

    @Test
    @DisplayName("extract() computes file counts and line changes correctly")
    void extract_WithFiles_ComputesCorrectStats() {
        List<ChangedFileDto> files = List.of(file("src/main.java", 10, 2), file("src/test.java", 5, 0));
        Map<String, Object> evidence = evidenceExtractor.extract(null, files);

        assertThat(evidence.get("fileCount")).isEqualTo(2);
        assertThat(evidence.get("totalAdditions")).isEqualTo(15);
        assertThat(evidence.get("totalDeletions")).isEqualTo(2);
        assertThat(evidence.get("totalLinesChanged")).isEqualTo(17);
    }

    @Test
    @DisplayName("extract() identifies security files correctly")
    void extract_WithSecurityFiles_IdentifiesThem() {
        List<ChangedFileDto> files = List.of(
                file("src/main/java/com/app/security/AuthService.java", 10, 2),
                file("src/main/java/com/app/controller/HomeController.java", 3, 1)
        );
        Map<String, Object> evidence = evidenceExtractor.extract(null, files);

        @SuppressWarnings("unchecked")
        List<String> securityFiles = (List<String>) evidence.get("securityFiles");
        assertThat(securityFiles).hasSize(1);
        assertThat(securityFiles.get(0)).contains("/security/");
    }

    @Test
    @DisplayName("extract() identifies test files correctly")
    void extract_WithTestFiles_IdentifiesThem() {
        List<ChangedFileDto> files = List.of(
                file("src/test/java/com/app/MainTest.java", 5, 0),
                file("src/main/java/com/app/Main.java", 10, 2)
        );
        Map<String, Object> evidence = evidenceExtractor.extract(null, files);

        @SuppressWarnings("unchecked")
        List<String> testFiles = (List<String>) evidence.get("testFiles");
        assertThat(testFiles).hasSize(1);
        assertThat(testFiles.get(0)).endsWith("Test.java");
    }

    @Test
    @DisplayName("extract() identifies config files correctly")
    void extract_WithConfigFiles_IdentifiesThem() {
        List<ChangedFileDto> files = List.of(
                file("src/main/resources/application.yml", 2, 0),
                file("src/main/java/com/app/Main.java", 10, 2)
        );
        Map<String, Object> evidence = evidenceExtractor.extract(null, files);

        @SuppressWarnings("unchecked")
        List<String> configFiles = (List<String>) evidence.get("configFiles");
        assertThat(configFiles).hasSize(1);
        assertThat(configFiles.get(0)).contains("application");
    }

    @Test
    @DisplayName("extract() identifies migration files correctly")
    void extract_WithMigrationFiles_IdentifiesThem() {
        List<ChangedFileDto> files = List.of(file("src/main/resources/db/migration/V2__add_users.sql", 15, 0));
        Map<String, Object> evidence = evidenceExtractor.extract(null, files);

        @SuppressWarnings("unchecked")
        List<String> migrationFiles = (List<String>) evidence.get("migrationFiles");
        assertThat(migrationFiles).hasSize(1);
        assertThat(migrationFiles.get(0)).endsWith(".sql");
    }

    @Test
    @DisplayName("extract() identifies dependency files correctly")
    void extract_WithDependencyFiles_IdentifiesThem() {
        List<ChangedFileDto> files = List.of(file("pom.xml", 5, 3));
        Map<String, Object> evidence = evidenceExtractor.extract(null, files);

        @SuppressWarnings("unchecked")
        List<String> depFiles = (List<String>) evidence.get("dependencyFiles");
        assertThat(depFiles).hasSize(1);
        assertThat(depFiles.get(0)).isEqualTo("pom.xml");
    }

    @Test
    @DisplayName("extract() handles empty files array gracefully")
    void extract_EmptyFiles_ReturnsEmptyEvidence() {
        Map<String, Object> evidence = evidenceExtractor.extract(List.of(), List.of());

        assertThat(evidence.get("fileCount")).isEqualTo(0);
        assertThat(evidence.get("totalAdditions")).isEqualTo(0);
        assertThat(evidence.get("totalDeletions")).isEqualTo(0);
    }

    @Test
    @DisplayName("extract() returns filenames list")
    void extract_ReturnsFilenamesList() {
        List<ChangedFileDto> files = List.of(file("src/main.java", 1, 0), file("src/test.java", 1, 0));
        Map<String, Object> evidence = evidenceExtractor.extract(null, files);

        @SuppressWarnings("unchecked")
        List<String> filenames = (List<String>) evidence.get("filenames");
        assertThat(filenames).containsExactly("src/main.java", "src/test.java");
    }
}
