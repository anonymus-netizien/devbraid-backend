package com.devbraid.indexing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_indexes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codebase_index_id", nullable = false)
    private CodebaseIndex codebaseIndex;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "language")
    private String language;

    @Column(name = "line_count")
    @Builder.Default
    private Integer lineCount = 0;

    @Column(name = "function_count")
    @Builder.Default
    private Integer functionCount = 0;

    @Column(name = "class_count")
    @Builder.Default
    private Integer classCount = 0;

    @Column(name = "imports", columnDefinition = "TEXT")
    private String imports;

    @Column(name = "exports", columnDefinition = "TEXT")
    private String exports;

    @Column(name = "functions", columnDefinition = "TEXT")
    private String functions;

    @Column(name = "classes", columnDefinition = "TEXT")
    private String classes;

    @Column(name = "dependencies", columnDefinition = "TEXT")
    private String dependencies;

    @Column(name = "risk_signals", columnDefinition = "TEXT")
    private String riskSignals;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
