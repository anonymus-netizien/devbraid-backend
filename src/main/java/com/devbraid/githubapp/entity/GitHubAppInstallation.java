package com.devbraid.githubapp.entity;

import com.devbraid.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "github_app_installations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class GitHubAppInstallation {

    @Id
    @Generated(event = EventType.INSERT)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "installation_id", nullable = false, unique = true)
    private Long installationId;

    @Column(name = "account_login", nullable = false, length = 255)
    private String accountLogin;

    @Column(name = "account_type", nullable = false, length = 50)
    @Builder.Default
    private String accountType = "User";

    @Column(name = "repository_selection", nullable = false, length = 50)
    @Builder.Default
    private String repositorySelection = "all";

    @Column(name = "access_token_encrypted")
    private byte[] accessTokenEncrypted;

    @Column(name = "access_token_iv")
    private byte[] accessTokenIv;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String permissions;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String events;

    @CreationTimestamp
    @Column(name = "installed_at", nullable = false, updatable = false)
    private OffsetDateTime installedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    // Setters for mutable fields
    public void setAccessTokenEncrypted(byte[] accessTokenEncrypted) {
        this.accessTokenEncrypted = accessTokenEncrypted;
    }

    public void setAccessTokenIv(byte[] accessTokenIv) {
        this.accessTokenIv = accessTokenIv;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public void setEvents(String events) {
        this.events = events;
    }

    public void setRepositorySelection(String repositorySelection) {
        this.repositorySelection = repositorySelection;
    }

    public void setSuspendedAt(OffsetDateTime suspendedAt) {
        this.suspendedAt = suspendedAt;
    }
}
