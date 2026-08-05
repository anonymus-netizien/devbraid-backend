package com.devbraid.githubapp.repository;

import com.devbraid.githubapp.entity.WebhookJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebhookJobRepository extends JpaRepository<WebhookJob, UUID> {

    /**
     * Claim a batch of due PENDING jobs, skipping rows locked by concurrent
     * workers. Must run inside a transaction — FOR UPDATE locks release at commit.
     */
    @Query(value = """
            SELECT * FROM webhook_jobs
            WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT :batch
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookJob> claimBatch(@Param("batch") int batch);
}
