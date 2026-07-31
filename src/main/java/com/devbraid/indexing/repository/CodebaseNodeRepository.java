package com.devbraid.indexing.repository;

import com.devbraid.indexing.entity.CodebaseNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CodebaseNodeRepository extends JpaRepository<CodebaseNode, UUID> {

    List<CodebaseNode> findByCodebaseIndexIdOrderByQualifiedName(UUID codebaseIndexId);

    void deleteByCodebaseIndexId(UUID codebaseIndexId);
}
