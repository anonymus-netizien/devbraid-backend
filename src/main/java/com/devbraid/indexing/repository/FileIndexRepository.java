package com.devbraid.indexing.repository;

import com.devbraid.indexing.entity.FileIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileIndexRepository extends JpaRepository<FileIndex, UUID> {

    List<FileIndex> findByCodebaseIndexIdOrderByFilePath(UUID codebaseIndexId);

    @Query("SELECT f FROM FileIndex f WHERE f.codebaseIndex.id = :indexId AND f.filePath LIKE %:pattern%")
    List<FileIndex> findByIndexIdAndPathPattern(@Param("indexId") UUID indexId, @Param("pattern") String pattern);

    @Query("SELECT f FROM FileIndex f WHERE f.codebaseIndex.id = :indexId AND f.language = :language")
    List<FileIndex> findByIndexIdAndLanguage(@Param("indexId") UUID indexId, @Param("language") String language);

    long countByCodebaseIndexId(UUID codebaseIndexId);
}
