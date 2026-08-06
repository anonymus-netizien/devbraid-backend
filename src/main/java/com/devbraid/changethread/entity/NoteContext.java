package com.devbraid.changethread.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What a decision note is anchored to. `COMMIT` requires a commit sha in `contextRef`, `FILE` requires a file path.")
public enum NoteContext {
    COMMIT,
    FILE,
    THREAD
}
