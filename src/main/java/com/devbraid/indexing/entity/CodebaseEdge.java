package com.devbraid.indexing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A directed relationship between two CodebaseNodes —
 * IMPORTS, EXTENDS, IMPLEMENTS, or CONTAINS.
 */
@Entity
@Table(name = "codebase_edges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodebaseEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codebase_index_id", nullable = false)
    private CodebaseIndex codebaseIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_node_id", nullable = false)
    private CodebaseNode sourceNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_node_id", nullable = false)
    private CodebaseNode targetNode;

    @Column(name = "edge_type", nullable = false, length = 32)
    private String edgeType;

    @Builder.Default
    private Integer weight = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
