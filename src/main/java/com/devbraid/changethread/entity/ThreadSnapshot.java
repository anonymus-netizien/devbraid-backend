package com.devbraid.changethread.entity;

import com.devbraid.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable snapshot of a ChangeThread's state at a point in time.
 * Created when the thread is first created, capturing the exact commits and files.
 * Used for reproducibility — "this is exactly what I reviewed".
 */
@Entity
@Table(name = "thread_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class ThreadSnapshot {

    @Id
    @Generated(event = EventType.INSERT)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private ChangeThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "repository_full_name", nullable = false, length = 500)
    private String repositoryFullName;

    @Column(name = "head_branch", nullable = false)
    private String headBranch;

    @Column(name = "base_branch", nullable = false)
    private String baseBranch;

    @Column(name = "commit_sha")
    private String commitSha;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String commits;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "changed_files", columnDefinition = "jsonb")
    private String changedFiles;

    @Column(nullable = false, length = 500)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SnapshotType type = SnapshotType.CREATION;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
