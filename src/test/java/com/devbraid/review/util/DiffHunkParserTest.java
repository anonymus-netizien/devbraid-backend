package com.devbraid.review.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DiffHunkParser — unified diff hunk parsing.
 */
@DisplayName("DiffHunkParser Unit Tests")
class DiffHunkParserTest {

    private static final String MULTI_HUNK_PATCH = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -1,3 +1,4 @@
             import a;
             import b;
            -removed line
            +added line 1
            +added line 2
            @@ -10,5 +10,4 @@
                 line10;
                 line11;
                 line12;
            -    oldA;
            -    oldB;
            +    newA;
            """;

    @Test
    @DisplayName("parseHunks() maps added/removed line numbers per hunk")
    void parseHunks_MultiHunkPatch_ParsesLineNumbers() {
        List<DiffHunk> hunks = DiffHunkParser.parseHunks(MULTI_HUNK_PATCH);

        assertThat(hunks).hasSize(2);

        DiffHunk first = hunks.get(0);
        assertThat(first.oldStart()).isEqualTo(1);
        assertThat(first.newStart()).isEqualTo(1);
        assertThat(first.addedLines()).containsExactly(3, 4);
        assertThat(first.removedLines()).containsExactly(3);

        DiffHunk second = hunks.get(1);
        assertThat(second.oldStart()).isEqualTo(10);
        assertThat(second.newStart()).isEqualTo(10);
        assertThat(second.addedLines()).containsExactly(13);
        assertThat(second.removedLines()).containsExactly(13, 14);
    }

    @Test
    @DisplayName("parseHunks() handles single-line hunk header without counts")
    void parseHunks_SingleLineHeader_MissingCountsDefaultToOne() {
        String patch = """
                @@ -1 +1 @@
                -old line
                +new line
                """;

        List<DiffHunk> hunks = DiffHunkParser.parseHunks(patch);

        assertThat(hunks).hasSize(1);
        assertThat(hunks.get(0).oldStart()).isEqualTo(1);
        assertThat(hunks.get(0).newStart()).isEqualTo(1);
        assertThat(hunks.get(0).addedLines()).containsExactly(1);
        assertThat(hunks.get(0).removedLines()).containsExactly(1);
    }

    @Test
    @DisplayName("parseHunks() parses new-file hunk starting at line 0")
    void parseHunks_NewFileHunk_AddedLinesFromOneToFive() {
        String patch = """
                @@ -0,0 +1,5 @@
                +line1
                +line2
                +line3
                +line4
                +line5
                """;

        List<DiffHunk> hunks = DiffHunkParser.parseHunks(patch);

        assertThat(hunks).hasSize(1);
        assertThat(hunks.get(0).oldStart()).isEqualTo(0);
        assertThat(hunks.get(0).newStart()).isEqualTo(1);
        assertThat(hunks.get(0).addedLines()).containsExactly(1, 2, 3, 4, 5);
        assertThat(hunks.get(0).removedLines()).isEmpty();
    }

    @Test
    @DisplayName("parseHunks() parses deleted-file hunk with empty added lines")
    void parseHunks_DeletedFileHunk_RemovedLinesOnly() {
        String patch = """
                @@ -1,5 +0,0 @@
                -line1
                -line2
                -line3
                -line4
                -line5
                """;

        List<DiffHunk> hunks = DiffHunkParser.parseHunks(patch);

        assertThat(hunks).hasSize(1);
        assertThat(hunks.get(0).oldStart()).isEqualTo(1);
        assertThat(hunks.get(0).newStart()).isEqualTo(0);
        assertThat(hunks.get(0).addedLines()).isEmpty();
        assertThat(hunks.get(0).removedLines()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("parseHunks() ignores '\\ No newline at end of file' markers")
    void parseHunks_NoNewlineMarker_Ignored() {
        String patch = """
                @@ -1,2 +1,2 @@
                -old line
                +new line
                \\ No newline at end of file
                """;

        List<DiffHunk> hunks = DiffHunkParser.parseHunks(patch);

        assertThat(hunks).hasSize(1);
        assertThat(hunks.get(0).addedLines()).containsExactly(1);
        assertThat(hunks.get(0).removedLines()).containsExactly(1);
    }

    @Test
    @DisplayName("parseHunks() ignores +++ and --- header lines inside hunk body")
    void parseHunks_FileHeaderLinesInsideBody_NotCounted() {
        String patch = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,2 +1,2 @@
                 context
                --- a/Foo.java
                +++ b/Foo.java
                +real added
                """;

        List<DiffHunk> hunks = DiffHunkParser.parseHunks(patch);

        assertThat(hunks).hasSize(1);
        assertThat(hunks.get(0).addedLines()).containsExactly(2);
        assertThat(hunks.get(0).removedLines()).isEmpty();
    }

    @Test
    @DisplayName("changedNewLines() unions added lines across hunks")
    void changedNewLines_MultipleHunks_ReturnsUnionOfAddedLines() {
        List<DiffHunk> hunks = DiffHunkParser.parseHunks(MULTI_HUNK_PATCH);

        Set<Integer> changed = DiffHunkParser.changedNewLines(hunks);

        assertThat(changed).containsExactlyInAnyOrder(3, 4, 13);
    }

    @Test
    @DisplayName("isChangedNewLine() only matches added lines, not context or removed")
    void isChangedNewLine_VariousLines_MatchesOnlyAdded() {
        List<DiffHunk> hunks = DiffHunkParser.parseHunks(MULTI_HUNK_PATCH);

        assertThat(DiffHunkParser.isChangedNewLine(hunks, 3)).isTrue();
        assertThat(DiffHunkParser.isChangedNewLine(hunks, 4)).isTrue();
        assertThat(DiffHunkParser.isChangedNewLine(hunks, 13)).isTrue();
        assertThat(DiffHunkParser.isChangedNewLine(hunks, 14)).isFalse();
        assertThat(DiffHunkParser.isChangedNewLine(hunks, 10)).isFalse();
        assertThat(DiffHunkParser.isChangedNewLine(hunks, 999)).isFalse();
    }

    @Test
    @DisplayName("parseHunks() returns empty list for null or blank patch")
    void parseHunks_NullOrBlank_ReturnsEmptyList() {
        assertThat(DiffHunkParser.parseHunks(null)).isEmpty();
        assertThat(DiffHunkParser.parseHunks("")).isEmpty();
        assertThat(DiffHunkParser.parseHunks("   \n ")).isEmpty();
        assertThat(DiffHunkParser.parseHunks("\n\n")).isEmpty();
    }
}
