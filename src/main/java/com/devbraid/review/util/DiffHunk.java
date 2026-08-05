package com.devbraid.review.util;

import java.util.List;

public record DiffHunk(int oldStart, int newStart, List<Integer> addedLines, List<Integer> removedLines) {
}
