package com.devbraid.analysis.service;

import com.devbraid.analysis.RiskLevel;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskFlagRulesEnhancedTest {

    private RiskFlagRules rules;

    @BeforeEach
    void setUp() {
        rules = new RiskFlagRules();
    }

    private static ChangedFileDto file(String name, int add, int del) {
        return new ChangedFileDto(name, "modified", add, del);
    }

    private static CommitSummaryDto commit(String message) {
        return new CommitSummaryDto("abc123", message, null);
    }

    @Test
    void detectSingleFileRisk_largeDiff_flagged() {
        List<ChangedFileDto> files = List.of(file("BigService.java", 200, 150));

        List<RiskFlagDto> flags = rules.evaluate(List.of(), files);

        assertTrue(flags.stream().anyMatch(f -> f.getRule().equals("singleFileLargeDiff")));
    }

    @Test
    void detectCrossCuttingConcerns_securityConfig_flagged() {
        List<ChangedFileDto> files = List.of(file("src/main/java/com/devbraid/SecurityConfig.java", 20, 5));

        List<RiskFlagDto> flags = rules.evaluate(List.of(), files);

        assertTrue(flags.stream().anyMatch(f -> f.getRule().equals("crossCuttingConcern")));
        assertEquals(RiskLevel.HIGH, flags.stream()
                .filter(f -> f.getRule().equals("crossCuttingConcern"))
                .findFirst().orElseThrow().getSeverity());
    }

    @Test
    void detectCommitRisks_revertCommit_flagged() {
        List<CommitSummaryDto> commits = List.of(commit("Revert feat(auth) add OAuth"));

        List<RiskFlagDto> flags = rules.evaluate(commits, List.of());

        assertTrue(flags.stream().anyMatch(f -> f.getRule().equals("revertCommits")));
    }

    @Test
    void detectCommitRisks_wipCommit_flagged() {
        List<CommitSummaryDto> commits = List.of(commit("WIP: working on dashboard"));

        List<RiskFlagDto> flags = rules.evaluate(commits, List.of());

        assertTrue(flags.stream().anyMatch(f -> f.getRule().equals("wipCommits")));
    }

    @Test
    void detectDependencyRisks_pomChange_flagged() {
        List<ChangedFileDto> files = List.of(file("pom.xml", 10, 5));

        List<RiskFlagDto> flags = rules.evaluate(List.of(), files);

        assertTrue(flags.stream().anyMatch(f -> f.getRule().equals("pomDependencyChange")));
    }

    @Test
    void calculateOverallRisk_empty_returnsNone() {
        assertEquals(RiskLevel.NONE, rules.calculateOverallRisk(List.of()));
    }

    @Test
    void calculateOverallRisk_mixed_returnsHighest() {
        List<RiskFlagDto> flags = List.of(
                RiskFlagDto.builder().rule("low").severity(RiskLevel.LOW).message("low").evidence(List.of()).build(),
                RiskFlagDto.builder().rule("high").severity(RiskLevel.HIGH).message("high").evidence(List.of()).build(),
                RiskFlagDto.builder().rule("med").severity(RiskLevel.MEDIUM).message("med").evidence(List.of()).build()
        );

        assertEquals(RiskLevel.HIGH, rules.calculateOverallRisk(flags));
    }
}
