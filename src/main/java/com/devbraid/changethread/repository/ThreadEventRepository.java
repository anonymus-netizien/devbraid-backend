package com.devbraid.changethread.repository;

import com.devbraid.changethread.entity.ThreadEvent;
import com.devbraid.changethread.entity.ThreadEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ThreadEventRepository extends JpaRepository<ThreadEvent, UUID> {

    List<ThreadEvent> findByThreadIdOrderByCreatedAtDesc(UUID threadId);

    Page<ThreadEvent> findByThreadIdOrderByCreatedAtDesc(UUID threadId, Pageable pageable);

    List<ThreadEvent> findByThreadIdAndTypeOrderByCreatedAtDesc(UUID threadId, ThreadEventType type);

    long countByThreadId(UUID threadId);
}
