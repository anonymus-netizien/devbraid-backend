package com.devbraid.analysis;

/**
 * Risk level classification for code changes.
 * Moved from changethread.entity to analysis module to resolve circular dependency.
 */
public enum RiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
