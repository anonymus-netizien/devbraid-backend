package com.devbraid.indexing.entity;

import com.devbraid.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "codebase_indexes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodebaseIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "repository", nullable = false, length = 500)
    private String repository;

    @Column(name = "branch", nullable = false)
    private String branch;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "total_files")
    @Builder.Default
    private Integer totalFiles = 0;

    @Column(name = "indexed_files")
    @Builder.Default
    private Integer indexedFiles = 0;

    @Column(name = "total_functions")
    @Builder.Default
    private Integer totalFunctions = 0;

    @Column(name = "total_classes")
    @Builder.Default
    private Integer totalClasses = 0;

    @Column(name = "total_dependencies")
    @Builder.Default
    private Integer totalDependencies = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
