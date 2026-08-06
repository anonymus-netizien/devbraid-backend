package com.devbraid.changethread.entity;

import com.devbraid.analysis.RiskLevel;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import com.devbraid.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "change_threads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class ChangeThread {

    @Id
    @Generated(event = EventType.INSERT)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "repository_full_name", nullable = false, length = 500)
    private String repositoryFullName;

    @Column(name = "head_branch", nullable = false)
    private String headBranch;

    @Column(name = "base_branch", nullable = false)
    @Builder.Default
    private String baseBranch = "main";

    @Column(nullable = false, length = 500)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ThreadSource source = ThreadSource.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ThreadStatus status = ThreadStatus.DRAFT;

    @Column(name = "commit_sha")
    private String commitSha;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<CommitSummaryDto> commits;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "changed_files", columnDefinition = "jsonb")
    private List<ChangedFileDto> changedFiles;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "risk_report", columnDefinition = "jsonb")
    private Map<String, Object> riskReport;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Setters for fields that need to be updated after construction
    public void setStatus(ThreadStatus status) {
        this.status = status;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public void setCommits(List<CommitSummaryDto> commits) {
        this.commits = commits;
    }

    public void setChangedFiles(List<ChangedFileDto> changedFiles) {
        this.changedFiles = changedFiles;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public void setRiskReport(Map<String, Object> riskReport) {
        this.riskReport = riskReport;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
