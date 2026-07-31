-- V15: Create codebase graph tables for AST-based dependency analysis
-- Nodes represent FILE/CLASS/METHOD/FIELD declarations; edges represent
-- IMPORTS/EXTENDS/IMPLEMENTS/CONTAINS relationships between them.

CREATE TABLE codebase_nodes
(
    id                UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    codebase_index_id UUID          NOT NULL REFERENCES codebase_indexes (id) ON DELETE CASCADE,
    file_index_id     UUID REFERENCES file_indexes (id) ON DELETE CASCADE,
    node_type         VARCHAR(32)   NOT NULL, -- FILE, CLASS, INTERFACE, METHOD, FIELD
    qualified_name    VARCHAR(512)  NOT NULL,
    file_path         VARCHAR(1024) NOT NULL,
    start_line        INT           NOT NULL DEFAULT 0,
    end_line          INT           NOT NULL DEFAULT 0,
    metadata          JSONB         NOT NULL DEFAULT '{}',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_codebase_node UNIQUE (codebase_index_id, node_type, qualified_name)
);

CREATE INDEX idx_nodes_codebase ON codebase_nodes (codebase_index_id);
CREATE INDEX idx_nodes_file ON codebase_nodes (file_index_id);

CREATE TABLE codebase_edges
(
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    codebase_index_id UUID        NOT NULL REFERENCES codebase_indexes (id) ON DELETE CASCADE,
    source_node_id    UUID        NOT NULL REFERENCES codebase_nodes (id) ON DELETE CASCADE,
    target_node_id    UUID        NOT NULL REFERENCES codebase_nodes (id) ON DELETE CASCADE,
    edge_type         VARCHAR(32) NOT NULL, -- IMPORTS, EXTENDS, IMPLEMENTS, CONTAINS
    weight            INT                  DEFAULT 1,
    metadata          JSONB                DEFAULT '{}',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_codebase_edge UNIQUE (codebase_index_id, source_node_id, target_node_id, edge_type)
);

CREATE INDEX idx_edges_source ON codebase_edges (source_node_id);
CREATE INDEX idx_edges_codebase ON codebase_edges (codebase_index_id);
