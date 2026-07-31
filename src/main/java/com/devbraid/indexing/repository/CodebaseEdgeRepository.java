package com.devbraid.indexing.repository;

import com.devbraid.indexing.entity.CodebaseEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CodebaseEdgeRepository extends JpaRepository<CodebaseEdge, UUID> {

    List<CodebaseEdge> findByCodebaseIndexId(UUID codebaseIndexId);

    void deleteByCodebaseIndexId(UUID codebaseIndexId);

    /**
     * Depth-limited transitive call/import reachability via recursive CTE.
     * Returns rows of (node_id, node_type, qualified_name, file_path, start_line, end_line, depth).
     */
    @Query(value = """
            WITH RECURSIVE reachable AS (
                SELECT e.target_node_id, 1 AS depth
                FROM codebase_edges e
                WHERE e.codebase_index_id = :codebaseId AND e.source_node_id = :nodeId
                UNION ALL
                SELECT e.target_node_id, r.depth + 1
                FROM codebase_edges e
                JOIN reachable r ON e.source_node_id = r.target_node_id
                WHERE e.codebase_index_id = :codebaseId AND r.depth < :depth
            )
            SELECT n.id, n.node_type, n.qualified_name, n.file_path, n.start_line, n.end_line, r.depth
            FROM reachable r
            JOIN codebase_nodes n ON n.id = r.target_node_id
            ORDER BY r.depth, n.qualified_name
            """, nativeQuery = true)
    List<Object[]> findReachableNodes(@Param("codebaseId") UUID codebaseId,
                                      @Param("nodeId") UUID nodeId,
                                      @Param("depth") int depth);
}
