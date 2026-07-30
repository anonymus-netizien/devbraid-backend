package com.devbraid.changethread.entity;

import com.devbraid.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable event entry in a thread's timeline.
 * Records every action taken on a thread: note added, analysis run, brief generated, etc.
 * Provides a chronological view of the thread's evolution.
 */
@Entity
@Table(name = "thread_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class ThreadEvent {

    @Id
    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private ChangeThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThreadEventType type;

    @Column(nullable = false, length = 500)
    private String summary;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
