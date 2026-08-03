package com.devbraid.changethread.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lifecycle status of a change thread: `DRAFT` → `ANALYZING` → `READY` → `PUBLISHED`.")
public enum ThreadStatus {
    DRAFT,
    ANALYZING,
    READY,
    PUBLISHED
}
