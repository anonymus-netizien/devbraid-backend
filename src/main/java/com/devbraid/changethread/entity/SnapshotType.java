package com.devbraid.changethread.entity;

/**
 * Type of snapshot — captures the reason the snapshot was taken.
 */
public enum SnapshotType {
    /**
     * Snapshot taken when the thread is first created.
     */
    CREATION,
    /**
     * Snapshot taken when the thread is refreshed from GitHub.
     */
    REFRESH,
    /**
     * Snapshot taken when analysis is run.
     */
    ANALYSIS,
    /**
     * Manual snapshot taken by the user.
     */
    MANUAL
}
