package com.devbraid.review.entity;

/**
 * Status of an automated PR review run.
 */
public enum ReviewStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    PUBLISHED
}
