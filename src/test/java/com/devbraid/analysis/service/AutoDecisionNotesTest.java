package com.devbraid.analysis.service;

import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoDecisionNotesTest {

    private AutoDecisionNotes autoDecisionNotes;

    @BeforeEach
    void setUp() {
        autoDecisionNotes = new AutoDecisionNotes();
    }

    private static ChangedFileDto file(String name, int add, int del) {
        return new ChangedFileDto(name, "modified", add, del);
    }

    private static CommitSummaryDto commit(String message) {
        return new CommitSummaryDto("abc123", message, null);
    }

    @Test
    void suggestNotes_nullInput_returnsEmpty() {
        var result = autoDecisionNotes.suggestNotes(null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void suggestNotes_securityFile_suggestsSecurityNote() {
        List<ChangedFileDto> files = List.of(file("src/main/java/com/devbraid/security/JwtFilter.java", 50, 10));

        var result = autoDecisionNotes.suggestNotes(null, files);

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(n -> n.category().equals("security")));
    }

    @Test
    void suggestNotes_migrationFile_suggestsDatabaseNote() {
        List<ChangedFileDto> files = List.of(file("V13__add_audit_columns.sql", 20, 0));

        var result = autoDecisionNotes.suggestNotes(null, files);

        assertTrue(result.stream().anyMatch(n -> n.category().equals("database")));
    }

    @Test
    void suggestNotes_largeFileChange_suggestsLargeChangeNote() {
        List<ChangedFileDto> files = List.of(file("src/main/java/com/devbraid/BigService.java", 200, 150));

        var result = autoDecisionNotes.suggestNotes(null, files);

        assertTrue(result.stream().anyMatch(n -> n.category().equals("large-change")));
    }

    @Test
    void suggestNotes_securityCommit_suggestsSecurityNote() {
        List<CommitSummaryDto> commits = List.of(commit("fix(security): patch XSS vulnerability in header"));

        var result = autoDecisionNotes.suggestNotes(commits, null);

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(n -> n.source() == AutoDecisionNotes.SuggestionSource.COMMIT_ANALYSIS));
    }

    @Test
    void suggestNotes_configFile_suggestsConfigNote() {
        List<ChangedFileDto> files = List.of(file("src/main/resources/application-prod.yml", 5, 3));

        var result = autoDecisionNotes.suggestNotes(null, files);

        assertTrue(result.stream().anyMatch(n -> n.category().equals("configuration")));
    }

    @Test
    void suggestNotes_apiController_suggestsApiNote() {
        List<ChangedFileDto> files = List.of(file("src/main/java/com/devbraid/UserController.java", 40, 10));

        var result = autoDecisionNotes.suggestNotes(null, files);

        assertTrue(result.stream().anyMatch(n -> n.category().equals("api")));
    }

    @Test
    void suggestNotes_noPatterns_returnsEmpty() {
        List<ChangedFileDto> files = List.of(file("README.md", 5, 0));

        var result = autoDecisionNotes.suggestNotes(null, files);

        assertTrue(result.isEmpty());
    }
}
