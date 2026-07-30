package com.devbraid.githubapp.repository;

import com.devbraid.githubapp.entity.GitHubAppInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubAppInstallationRepository extends JpaRepository<GitHubAppInstallation, UUID> {

    Optional<GitHubAppInstallation> findByInstallationId(Long installationId);

    List<GitHubAppInstallation> findByUserId(UUID userId);

    boolean existsByInstallationId(Long installationId);
}
