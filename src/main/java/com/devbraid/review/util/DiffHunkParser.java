package com.devbraid.review.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class DiffHunkParser {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

    private DiffHunkParser() {
    }

    public static List<DiffHunk> parseHunks(String patch) {
        if (patch == null || patch.isBlank()) {
            return List.of();
        }
        List<DiffHunk> hunks = new ArrayList<>();
        DiffHunk current = null;
        int oldLine = 0;
        int newLine = 0;
        for (String line : patch.split("\n", -1)) {
            Matcher header = HUNK_HEADER.matcher(line);
            if (header.matches()) {
                if (current != null) {
                    hunks.add(current);
                }
                current = new DiffHunk(Integer.parseInt(header.group(1)), Integer.parseInt(header.group(3)),
                        new ArrayList<>(), new ArrayList<>());
                oldLine = current.oldStart();
                newLine = current.newStart();
            } else if (current != null) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    current.addedLines().add(newLine);
                    newLine++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    current.removedLines().add(oldLine);
                    oldLine++;
                } else if (line.startsWith(" ")) {
                    oldLine++;
                    newLine++;
                }
            }
        }
        if (current != null) {
            hunks.add(current);
        }
        return hunks;
    }

    public static Set<Integer> changedNewLines(List<DiffHunk> hunks) {
        return hunks.stream()
                .flatMap(hunk -> hunk.addedLines().stream())
                .collect(Collectors.toSet());
    }

    public static boolean isChangedNewLine(List<DiffHunk> hunks, int newLineNumber) {
        return hunks.stream().anyMatch(hunk -> hunk.addedLines().contains(newLineNumber));
    }
}
