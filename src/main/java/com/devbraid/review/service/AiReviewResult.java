package com.devbraid.review.service;

import java.util.List;

/**
 * Parsed, validated AI review output. {@code summary} may be null when the AI
 * provided none; findings are already validated against the diff.
 */
public record AiReviewResult(String summary, List<ReviewFinding> findings) {
}
