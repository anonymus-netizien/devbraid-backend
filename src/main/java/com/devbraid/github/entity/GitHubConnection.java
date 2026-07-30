package com.devbraid.github.entity;

import com.devbraid.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "github_connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class GitHubConnection {

    @Id
    @Generated(event = EventType.INSERT)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "encrypted_pat", nullable = false)
    private byte[] encryptedPat;

    @Column(nullable = false)
    private byte[] iv;

    @Column(name = "github_username")
    private String githubUsername;

    @CreationTimestamp
    @Column(name = "connected_at", nullable = false, updatable = false)
    private OffsetDateTime connectedAt;

    @Column(name = "last_validated")
    private OffsetDateTime lastValidated;

    public void setLastValidated(OffsetDateTime lastValidated) {
        this.lastValidated = lastValidated;
    }
}
