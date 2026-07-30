-- V13: Create codebase indexing tables for AI-powered code graph analysis
-- Enables function-level, class-level, and dependency tracking for deeper risk analysis

CREATE TABLE codebase_indexes
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    repository         VARCHAR(500) NOT NULL,
    branch             VARCHAR(255) NOT NULL,
    status             VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    total_files        INTEGER               DEFAULT 0,
    indexed_files      INTEGER               DEFAULT 0,
    total_functions    INTEGER               DEFAULT 0,
    total_classes      INTEGER               DEFAULT 0,
    total_dependencies INTEGER               DEFAULT 0,
    error_message      TEXT,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_codebase_indexes_user ON codebase_indexes (user_id);
CREATE INDEX idx_codebase_indexes_repo ON codebase_indexes (repository, branch);
CREATE INDEX idx_codebase_indexes_status ON codebase_indexes (status);

CREATE TABLE file_indexes
(
    id                UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    codebase_index_id UUID          NOT NULL REFERENCES codebase_indexes (id) ON DELETE CASCADE,
    file_path         VARCHAR(1000) NOT NULL,
    file_type         VARCHAR(50),
    language          VARCHAR(50),
    line_count        INTEGER                DEFAULT 0,
    function_count    INTEGER                DEFAULT 0,
    class_count       INTEGER                DEFAULT 0,
    imports           TEXT,
    exports           TEXT,
    functions         TEXT,
    classes           TEXT,
    dependencies      TEXT,
    risk_signals      TEXT,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_file_indexes_codebase ON file_indexes (codebase_index_id);
CREATE INDEX idx_file_indexes_path ON file_indexes (file_path);
CREATE INDEX idx_file_indexes_type ON file_indexes (file_type);
