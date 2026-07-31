package com.devbraid.analysis.service;

import com.devbraid.github.dto.response.ChangedFileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCoverageGapDetectorTest {

    private TestCoverageGapDetector detector;

    @BeforeEach
    void setUp() {
        detector = new TestCoverageGapDetector();
    }

    private static ChangedFileDto file(String name, int add, int del) {
        return new ChangedFileDto(name, "modified", add, del);
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
        List<ChangedFileDto> files = List.of(
                file("src/main/java/com/devbraid/UserService.java", 50, 10),
                file("src/main/java/com/devbraid/AuthController.java", 30, 5)
        );

        var result = detector.analyzeTestCoverage(files);

        assertEquals(2, result.productionFileCount());
        assertEquals(0, result.testFileCount());
        assertFalse(result.recommendations().isEmpty());
        assertTrue(result.recommendations().stream().anyMatch(r -> r.contains("No test files changed")));
    }

    @Test
    void analyzeTestCoverage_withTests_noFlags() {
        List<ChangedFileDto> files = List.of(
                file("src/main/java/com/devbraid/UserService.java", 50, 10),
                file("src/test/java/com/devbraid/UserServiceTest.java", 30, 0)
        );

        var result = detector.analyzeTestCoverage(files);

        assertEquals(1, result.productionFileCount());
        assertEquals(1, result.testFileCount());
    }

    @Test
    void analyzeTestCoverage_securityFileUntested_highRisk() {
        List<ChangedFileDto> files = List.of(
                file("src/main/java/com/devbraid/security/JwtFilter.java", 80, 20),
                file("src/main/java/com/devbraid/service/UserService.java", 50, 10)
        );

        var result = detector.analyzeTestCoverage(files);

        assertFalse(result.highRiskUntestedFiles().isEmpty());
        assertTrue(result.highRiskUntestedFiles().stream().anyMatch(f -> f.contains("JwtFilter")));
    }

    @Test
    void analyzeTestCoverage_infraFilesIgnored() {
        List<ChangedFileDto> files = List.of(
                file("Dockerfile", 5, 0),
                file("application.yml", 10, 2)
        );

        var result = detector.analyzeTestCoverage(files);

        assertEquals(0, result.productionFileCount());
        assertEquals(0, result.testFileCount());
    }

    @Test
    void analyzeTestCoverage_tsxFiles() {
        List<ChangedFileDto> files = List.of(
                file("src/components/Dashboard.tsx", 100, 20),
                file("src/components/__tests__/Dashboard.test.tsx", 40, 0)
        );

        var result = detector.analyzeTestCoverage(files);

        assertEquals(1, result.productionFileCount());
        assertEquals(1, result.testFileCount());
    }
}
