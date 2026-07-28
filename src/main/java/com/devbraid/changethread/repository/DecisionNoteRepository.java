package com.devbraid.changethread.repository;

import com.devbraid.changethread.entity.DecisionNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DecisionNoteRepository extends JpaRepository<DecisionNote, UUID> {

    List<DecisionNote> findByThreadIdOrderByCreatedAtDesc(UUID threadId);

    List<DecisionNote> findByThreadIdAndAuthorId(UUID threadId, UUID authorId);
}
