package com.devbraid.changethread.repository;

import com.devbraid.changethread.entity.DecisionNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionNoteRepository extends JpaRepository<DecisionNote, UUID> {

    List<DecisionNote> findByThreadIdOrderByCreatedAtDesc(UUID threadId);

    Optional<DecisionNote> findByIdAndAuthorId(UUID id, UUID authorId);

    List<DecisionNote> findByThreadIdAndAuthorId(UUID threadId, UUID authorId);

    /**
     * Batch-fetch notes for multiple thread IDs — eliminates N+1 queries.
     * Used by ChangeThreadService to load all notes in one query for paginated lists.
     */
    List<DecisionNote> findByThreadIdInOrderByCreatedAtDesc(List<UUID> threadIds);

    @Query(value = "SELECT n FROM DecisionNote n JOIN FETCH n.thread t WHERE n.author.id = :userId ORDER BY n.createdAt DESC",
            countQuery = "SELECT count(n) FROM DecisionNote n WHERE n.author.id = :userId")
    Page<DecisionNote> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);
}
