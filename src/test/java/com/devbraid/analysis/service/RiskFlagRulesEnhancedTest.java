package com.devbraid.analysis.service;

import com.devbraid.analysis.RiskLevel;
import com.devbraid.analysis.dto.RiskFlagDto;
import com.devbraid.analysis.util.JsonParseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskFlagRulesEnhancedTest {

    private RiskFlagRules rules;

    @BeforeEach
    void setUp() {
        rules = new RiskFlagRules(new JsonParseUtils(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    void detectSingleFileRisk_largeDiff_flagged() {
        String files = """
                [{"filename": "BigService.java", "additions": 200, "deletions": 150}]
                """;

        List<RiskFlagDto> flags = rules.evaluate("[]", files);

        assertTrue(flags.stream().anyMatch(f -> f.getRule().equals("singleFileLargeDiff")));
    }

    @Test
    void detectCrossCuttingConcerns_securityConfig_flagged() {
        String files = """
                [{"filename": "src/main/java/com/devbraid/SecurityConfig.java", "additions": 20, "deletions": 5}]
                """;

        List<RiskFlagDto> flags = rules.evaluate("[]", files);

        assertTrue(flags.stream().anyMatch(f -> f.getRule().equals("crossCuttingConcern")));
        assertEquals(RiskLevel.HIGH, flags.stream()
                .filter(f -> f.getRule().equals("crossCuttingConcern"))
                .findFirst().orElseThrow().getSeverity());
    }

    @Test
    void detectCommitRisks_revertCommit_flagged() {
        String commits = "[{\"message\": \"Revert feat(auth) add OAuth\"}]";

        List<RiskFlagDto> flags = rules.evaluate(commits, "[]");

        assertTrue(flags.stream().anyMatch(f -> f.getRule().equals("revertCommits")));
    }

    @Test
    void detectCommitRisks_wipCommit_flagged() {
        String commits = """
                [{"message": "WIP: working on dashboard"}]
                """;

        List<RiskFlagDto> flags = rules.evaluate(commits, "[]");

        assertTrue(flags.stream().anyMatch(f -> f.getRule().equals("wipCommits")));
    }

    @Test
    void detectDependencyRisks_pomChange_flagged() {
        String files = """
                [{"filename": "pom.xml", "additions": 10, "deletions": 5}]
                """;

        List<RiskFlagDto> flags = rules.evaluate("[]", files);

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
