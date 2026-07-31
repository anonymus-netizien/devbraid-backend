package com.devbraid.brief.repository;

import com.devbraid.brief.entity.ChangeBrief;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChangeBriefRepository extends JpaRepository<ChangeBrief, UUID> {

    Optional<ChangeBrief> findByThreadId(UUID threadId);

    Optional<ChangeBrief> findByIdAndThread_UserId(UUID id, UUID userId);

    @Query(value = "SELECT b FROM ChangeBrief b JOIN FETCH b.thread t WHERE t.user.id = :userId ORDER BY b.createdAt DESC",
            countQuery = "SELECT count(b) FROM ChangeBrief b WHERE b.thread.user.id = :userId")
    Page<ChangeBrief> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);
}
