package com.devbraid.githubapp.repository;

import com.devbraid.githubapp.entity.GitHubWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubWebhookRepository extends JpaRepository<GitHubWebhook, UUID> {

    Optional<GitHubWebhook> findByDeliveryId(String deliveryId);

    List<GitHubWebhook> findByInstallationIdAndProcessedFalse(Long installationId);

    boolean existsByDeliveryId(String deliveryId);
}
