package com.devbraid.changethread.repository;

import com.devbraid.changethread.entity.ChangeThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ChangeThreadRepository extends JpaRepository<ChangeThread, UUID>, JpaSpecificationExecutor<ChangeThread> {

    Page<ChangeThread> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<ChangeThread> findByIdAndUserId(UUID id, UUID userId);

    Page<ChangeThread> findByCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime start, OffsetDateTime end, Pageable pageable);
}
