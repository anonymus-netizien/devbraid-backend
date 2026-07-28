package com.devbraid.brief.repository;

import com.devbraid.brief.entity.ChangeBrief;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChangeBriefRepository extends JpaRepository<ChangeBrief, UUID> {

    Optional<ChangeBrief> findByThreadId(UUID threadId);
}
