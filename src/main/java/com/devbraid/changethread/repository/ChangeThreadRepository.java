package com.devbraid.changethread.repository;

import com.devbraid.changethread.entity.ChangeThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChangeThreadRepository extends JpaRepository<ChangeThread, UUID> {

    Page<ChangeThread> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<ChangeThread> findByIdAndUserId(UUID id, UUID userId);
}
