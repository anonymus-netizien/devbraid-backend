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
 * A node in the codebase graph — a FILE, CLASS, INTERFACE, METHOD, or FIELD
 * declaration extracted via AST parsing. Nodes are linked by CodebaseEdge.
 */
@Entity
@Table(name = "codebase_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodebaseNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codebase_index_id", nullable = false)
    private CodebaseIndex codebaseIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_index_id")
    private FileIndex fileIndex;

    @Column(name = "node_type", nullable = false, length = 32)
    private String nodeType;

    @Column(name = "qualified_name", nullable = false, length = 512)
    private String qualifiedName;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "start_line", nullable = false)
    @Builder.Default
    private Integer startLine = 0;

    @Column(name = "end_line", nullable = false)
    @Builder.Default
    private Integer endLine = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
