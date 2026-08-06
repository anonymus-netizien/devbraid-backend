package com.devbraid.changethread.entity;

import com.devbraid.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "decision_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class DecisionNote {

    @Id
    @Generated(event = EventType.INSERT)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private ChangeThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NoteContext context;

    @Column(name = "context_ref", length = 500)
    private String contextRef;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String decision;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @Column(columnDefinition = "TEXT")
    private String alternatives;

    @Column(columnDefinition = "TEXT")
    private String impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NoteStatus status = NoteStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Setters for update operations
    public void setDecision(String decision) {
        this.decision = decision;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public void setAlternatives(String alternatives) {
        this.alternatives = alternatives;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public void setStatus(NoteStatus status) {
        this.status = status;
    }
}
