package com.devbraid.analysis;

import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.analysis.service.RiskFlagRules;
import com.devbraid.github.dto.response.ChangedFileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RiskFlagRules — deterministic risk evaluation rules.
 * Tests pure function logic without Spring context.
 */
@DisplayName("RiskFlagRules Unit Tests")
class RiskFlagRulesTest {

    private RiskFlagRules riskFlagRules;

    private static ChangedFileDto file(String name, int add, int del) {
        return new ChangedFileDto(name, "modified", add, del);
    }

    @BeforeEach
    void setUp() {
        riskFlagRules = new RiskFlagRules();
    }

    @Test
    @DisplayName("evaluate() returns empty flags for empty input")
    void evaluate_EmptyInput_ReturnsEmptyFlags() {
        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, null);
        assertThat(flags).isEmpty();
    }

    @Test
    @DisplayName("evaluate() flags large diff when lines > 500")
    void evaluate_LargeDiff_ReturnsMediumFlag() {
        List<ChangedFileDto> files = List.of(file("src/main.java", 300, 250));
        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, files);

        assertThat(flags).anyMatch(f ->
                "largeDiff".equals(f.getRule()) && f.getSeverity() == RiskLevel.MEDIUM
        );
    }

    @Test
    @DisplayName("evaluate() flags many files when count > 20")
    void evaluate_ManyFiles_ReturnsMediumFlag() {
        List<ChangedFileDto> files = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            files.add(file("file" + i + ".java", 1, 0));
        }

        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, files);

        assertThat(flags).anyMatch(f ->
                "manyFiles".equals(f.getRule()) && f.getSeverity() == RiskLevel.MEDIUM
        );
    }

    @Test
    @DisplayName("evaluate() flags security paths")
    void evaluate_SecurityPaths_ReturnsHighFlag() {
        List<ChangedFileDto> files = List.of(file("src/main/java/com/app/security/AuthService.java", 10, 2));
        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, files);

        assertThat(flags).anyMatch(f ->
                "securityPaths".equals(f.getRule()) && f.getSeverity() == RiskLevel.HIGH
        );
    }

    @Test
    @DisplayName("evaluate() flags missing tests")
    void evaluate_NoTests_ReturnsLowFlag() {
        List<ChangedFileDto> files = List.of(file("src/main.java", 10, 2));
        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, files);

        assertThat(flags).anyMatch(f ->
                "noTests".equals(f.getRule()) && f.getSeverity() == RiskLevel.LOW
        );
    }

    @Test
    @DisplayName("evaluate() does not flag noTests when test files are present")
    void evaluate_WithTests_DoesNotFlagNoTests() {
        List<ChangedFileDto> files = List.of(
                file("src/main.java", 10, 2),
                file("src/test/java/MainTest.java", 5, 0)
        );
        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, files);

        assertThat(flags).noneMatch(f -> "noTests".equals(f.getRule()));
    }

    @Test
    @DisplayName("evaluate() flags config changes")
    void evaluate_ConfigChanges_ReturnsLowFlag() {
        List<ChangedFileDto> files = List.of(file("src/main/resources/application.yml", 2, 1));
        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, files);

        assertThat(flags).anyMatch(f ->
                "configChanges".equals(f.getRule()) && f.getSeverity() == RiskLevel.LOW
        );
    }

    @Test
    @DisplayName("evaluate() flags migration files")
    void evaluate_MigrationFiles_ReturnsMediumFlag() {
        List<ChangedFileDto> files = List.of(file("src/main/resources/db/migration/V2__add_users.sql", 15, 0));
        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, files);

        assertThat(flags).anyMatch(f ->
                "migrationFiles".equals(f.getRule()) && f.getSeverity() == RiskLevel.MEDIUM
        );
    }

    @Test
    @DisplayName("evaluate() flags dependency changes")
    void evaluate_DependencyChanges_ReturnsLowFlag() {
        List<ChangedFileDto> files = List.of(file("pom.xml", 5, 3));
        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, files);

        assertThat(flags).anyMatch(f ->
                f.getRule().contains("Dependency") && f.getSeverity() == RiskLevel.MEDIUM
        );
    }

    @Test
    @DisplayName("calculateOverallRisk() returns NONE for empty flags")
    void calculateOverallRisk_EmptyFlags_ReturnsNone() {
        assertThat(riskFlagRules.calculateOverallRisk(List.of())).isEqualTo(RiskLevel.NONE);
    }

    @Test
    @DisplayName("calculateOverallRisk() returns highest severity from flags")
    void calculateOverallRisk_MultipleFlags_ReturnsHighest() {
        List<RiskFlagDto> flags = List.of(
                RiskFlagDto.builder().rule("noTests").severity(RiskLevel.LOW).message("No tests").build(),
                RiskFlagDto.builder().rule("largeDiff").severity(RiskLevel.MEDIUM).message("Large diff").build(),
                RiskFlagDto.builder().rule("securityPaths").severity(RiskLevel.HIGH).message("Security changes").build()
        );

        assertThat(riskFlagRules.calculateOverallRisk(flags)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("evaluate() sorts flags by severity descending")
    void evaluate_SortsFlagsBySeverity() {
        List<ChangedFileDto> files = List.of(file("src/main/java/com/app/security/AuthService.java", 600, 0));
        List<RiskFlagDto> flags = riskFlagRules.evaluate(null, files);

        // HIGH (securityPaths) should come before MEDIUM (largeDiff)
        if (flags.size() >= 2) {
            assertThat(flags.get(0).getSeverity()).isGreaterThanOrEqualTo(flags.get(1).getSeverity());
        }
    }
}
