package com.devbraid.indexing.repository;

import com.devbraid.indexing.entity.CodebaseIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CodebaseIndexRepository extends JpaRepository<CodebaseIndex, UUID> {

    List<CodebaseIndex> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<CodebaseIndex> findByUserIdAndRepositoryAndBranch(UUID userId, String repository, String branch);

    List<CodebaseIndex> findByRepositoryAndBranch(String repository, String branch);

    @Query("SELECT c FROM CodebaseIndex c WHERE c.user.id = :userId AND c.status = :status")
    List<CodebaseIndex> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status);
}
