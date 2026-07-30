package com.devbraid.analysis.service;

import com.devbraid.analysis.util.JsonParseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoDecisionNotesTest {

    private AutoDecisionNotes autoDecisionNotes;

    @BeforeEach
    void setUp() {
        autoDecisionNotes = new AutoDecisionNotes(new JsonParseUtils(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    void suggestNotes_nullInput_returnsEmpty() {
        var result = autoDecisionNotes.suggestNotes(null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void suggestNotes_securityFile_suggestsSecurityNote() {
        String json = """
                [
                    {"filename": "src/main/java/com/devbraid/security/JwtFilter.java", "additions": 50, "deletions": 10}
                ]
                """;

        var result = autoDecisionNotes.suggestNotes(null, json);

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(n -> n.category().equals("security")));
    }

    @Test
    void suggestNotes_migrationFile_suggestsDatabaseNote() {
        String json = """
                [
                    {"filename": "V13__add_audit_columns.sql", "additions": 20, "deletions": 0}
                ]
                """;

        var result = autoDecisionNotes.suggestNotes(null, json);

        assertTrue(result.stream().anyMatch(n -> n.category().equals("database")));
    }

    @Test
    void suggestNotes_largeFileChange_suggestsLargeChangeNote() {
        String json = """
                [
                    {"filename": "src/main/java/com/devbraid/BigService.java", "additions": 200, "deletions": 150}
                ]
                """;

        var result = autoDecisionNotes.suggestNotes(null, json);

        assertTrue(result.stream().anyMatch(n -> n.category().equals("large-change")));
    }

    @Test
    void suggestNotes_securityCommit_suggestsSecurityNote() {
        String commitsJson = """
                [
                    {"message": "fix(security): patch XSS vulnerability in header"}
                ]
                """;

        var result = autoDecisionNotes.suggestNotes(commitsJson, null);

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(n -> n.source() == AutoDecisionNotes.SuggestionSource.COMMIT_ANALYSIS));
    }

    @Test
    void suggestNotes_configFile_suggestsConfigNote() {
        String json = """
                [
                    {"filename": "src/main/resources/application-prod.yml", "additions": 5, "deletions": 3}
                ]
                """;

        var result = autoDecisionNotes.suggestNotes(null, json);

        assertTrue(result.stream().anyMatch(n -> n.category().equals("configuration")));
    }

    @Test
    void suggestNotes_apiController_suggestsApiNote() {
        String json = """
                [
                    {"filename": "src/main/java/com/devbraid/UserController.java", "additions": 40, "deletions": 10}
                ]
                """;

        var result = autoDecisionNotes.suggestNotes(null, json);

        assertTrue(result.stream().anyMatch(n -> n.category().equals("api")));
    }

    @Test
    void suggestNotes_noPatterns_returnsEmpty() {
        String json = """
                [
                    {"filename": "README.md", "additions": 5, "deletions": 0}
                ]
                """;

        var result = autoDecisionNotes.suggestNotes(null, json);

        assertTrue(result.isEmpty());
    }
}
