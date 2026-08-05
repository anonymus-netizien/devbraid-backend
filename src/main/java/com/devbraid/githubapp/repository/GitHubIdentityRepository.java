package com.devbraid.githubapp.repository;

import com.devbraid.githubapp.entity.GitHubIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GitHubIdentityRepository extends JpaRepository<GitHubIdentity, UUID> {

    Optional<GitHubIdentity> findByGithubUserId(Long githubUserId);

    Optional<GitHubIdentity> findByUserId(UUID userId);
}
