package com.devbraid.changethread.entity;

/**
 * Type of event in a thread's timeline.
 * Each action on a thread is recorded as one of these event types.
 * Named ThreadEventType to avoid collision with org.hibernate.generator.EventType.
 */
public enum ThreadEventType {
    /**
     * Thread was created.
     */
    THREAD_CREATED,
    /**
     * Thread was refreshed from GitHub.
     */
    THREAD_REFRESHED,
    /**
     * Thread status was changed.
     */
    STATUS_CHANGED,
    /**
     * Decision note was added.
     */
    NOTE_ADDED,
    /**
     * Decision note was updated.
     */
    NOTE_UPDATED,
    /**
     * Decision note was deleted.
     */
    NOTE_DELETED,
    /**
     * File comment was added.
     */
    FILE_COMMENT_ADDED,
    /**
     * File comment was updated.
     */
    FILE_COMMENT_UPDATED,
    /**
     * File comment was deleted.
     */
    FILE_COMMENT_DELETED,
    /**
     * Risk analysis was run.
     */
    ANALYSIS_RUN,
    /**
     * Brief was generated.
     */
    BRIEF_GENERATED,
    /**
     * Brief was published to GitHub.
     */
    BRIEF_PUBLISHED,
    /**
     * Snapshot was created.
     */
    SNAPSHOT_CREATED,
    /**
     * Manual event added by user.
     */
    MANUAL
}
