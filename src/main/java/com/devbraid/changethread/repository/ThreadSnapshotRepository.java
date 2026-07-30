package com.devbraid.changethread.repository;

import com.devbraid.changethread.entity.SnapshotType;
import com.devbraid.changethread.entity.ThreadSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThreadSnapshotRepository extends JpaRepository<ThreadSnapshot, UUID> {

    List<ThreadSnapshot> findByThreadIdOrderByCreatedAtDesc(UUID threadId);

    Optional<ThreadSnapshot> findFirstByThreadIdOrderByCreatedAtDesc(UUID threadId);

    Optional<ThreadSnapshot> findByIdAndUserId(UUID id, UUID userId);

    List<ThreadSnapshot> findByThreadIdAndTypeOrderByCreatedAtDesc(UUID threadId, SnapshotType type);
}
