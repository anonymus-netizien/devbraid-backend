package com.devbraid.changethread.repository;

import com.devbraid.changethread.entity.FileComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileCommentRepository extends JpaRepository<FileComment, UUID> {

    List<FileComment> findByThreadIdOrderByCreatedAtDesc(UUID threadId);

    List<FileComment> findByThreadIdAndFilePathOrderByCreatedAtAsc(UUID threadId, String filePath);

    List<FileComment> findByThreadIdAndAuthorIdOrderByCreatedAtDesc(UUID threadId, UUID authorId);

    Optional<FileComment> findByIdAndAuthorId(UUID id, UUID authorId);

    long countByThreadId(UUID threadId);
}
