package com.devbraid.review.service;

import com.devbraid.review.entity.FindingCategory;
import com.devbraid.review.entity.FindingSeverity;

/**
 * A single review finding. Line-level findings become GitHub inline comments;
 * file-level findings (null line) are listed in the review summary.
 */
public record ReviewFinding(
        FindingSeverity severity,
        FindingCategory category,
        String filePath,
        Integer lineNumber,
        String title,
        String body) {
}
