package com.devbraid.analysis.service;

import com.devbraid.analysis.util.JsonParseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCoverageGapDetectorTest {

    private TestCoverageGapDetector detector;

    @BeforeEach
    void setUp() {
        detector = new TestCoverageGapDetector(new JsonParseUtils(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    void analyzeTestCoverage_nullInput_returnsEmpty() {
        var result = detector.analyzeTestCoverage(null);
        assertEquals(0, result.productionFileCount());
        assertEquals(0, result.testFileCount());
        assertTrue(result.recommendations().isEmpty());
    }

    @Test
    void analyzeTestCoverage_prodFilesWithoutTests_flags() {
        String json = """
                [
                    {"filename": "src/main/java/com/devbraid/UserService.java", "additions": 50, "deletions": 10},
                    {"filename": "src/main/java/com/devbraid/AuthController.java", "additions": 30, "deletions": 5}
                ]
                """;

        var result = detector.analyzeTestCoverage(json);

        assertEquals(2, result.productionFileCount());
        assertEquals(0, result.testFileCount());
        assertFalse(result.recommendations().isEmpty());
        assertTrue(result.recommendations().stream().anyMatch(r -> r.contains("No test files changed")));
    }

    @Test
    void analyzeTestCoverage_withTests_noFlags() {
        String json = """
                [
                    {"filename": "src/main/java/com/devbraid/UserService.java", "additions": 50, "deletions": 10},
                    {"filename": "src/test/java/com/devbraid/UserServiceTest.java", "additions": 30, "deletions": 0}
                ]
                """;

        var result = detector.analyzeTestCoverage(json);

        assertEquals(1, result.productionFileCount());
        assertEquals(1, result.testFileCount());
    }

    @Test
    void analyzeTestCoverage_securityFileUntested_highRisk() {
        String json = """
                [
                    {"filename": "src/main/java/com/devbraid/security/JwtFilter.java", "additions": 80, "deletions": 20},
                    {"filename": "src/main/java/com/devbraid/service/UserService.java", "additions": 50, "deletions": 10}
                ]
                """;

        var result = detector.analyzeTestCoverage(json);

        assertFalse(result.highRiskUntestedFiles().isEmpty());
        assertTrue(result.highRiskUntestedFiles().stream().anyMatch(f -> f.contains("JwtFilter")));
    }

    @Test
    void analyzeTestCoverage_infraFilesIgnored() {
        String json = """
                [
                    {"filename": "Dockerfile", "additions": 5, "deletions": 0},
                    {"filename": "application.yml", "additions": 10, "deletions": 2}
                ]
                """;

        var result = detector.analyzeTestCoverage(json);

        assertEquals(0, result.productionFileCount());
        assertEquals(0, result.testFileCount());
    }

    @Test
    void analyzeTestCoverage_tsxFiles() {
        String json = """
                [
                    {"filename": "src/components/Dashboard.tsx", "additions": 100, "deletions": 20},
                    {"filename": "src/components/__tests__/Dashboard.test.tsx", "additions": 40, "deletions": 0}
                ]
                """;

        var result = detector.analyzeTestCoverage(json);

        assertEquals(1, result.productionFileCount());
        assertEquals(1, result.testFileCount());
    }
}
