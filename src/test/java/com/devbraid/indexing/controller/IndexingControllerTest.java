package com.devbraid.indexing.controller;

import com.devbraid.indexing.entity.CodebaseIndex;
import com.devbraid.indexing.entity.FileIndex;
import com.devbraid.indexing.service.IndexingService;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexingControllerTest {

    @Mock
    private IndexingService indexingService;

    @InjectMocks
    private IndexingController controller;

    private User testUser;
    private CodebaseIndex testIndex;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .build();

        testIndex = CodebaseIndex.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .repository("owner/repo")
                .branch("main")
                .status("PENDING")
                .build();
    }

    @Test
    void getIndex_exists_returnsOk() {
        when(indexingService.getIndex(testIndex.getId()))
                .thenReturn(Optional.of(testIndex));

        ResponseEntity<?> response = controller.getIndex(testIndex.getId());

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getIndex_notExists_returns404() {
        when(indexingService.getIndex(any()))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getIndex(UUID.randomUUID());

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void listIndexes_returnsList() {
        when(indexingService.listIndexesByUser(testUser)).thenReturn(List.of(testIndex));

        ResponseEntity<?> response = controller.listIndexes(testUser);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getFiles_returnsFiles() {
        FileIndex fileIndex = FileIndex.builder()
                .id(UUID.randomUUID())
                .codebaseIndex(testIndex)
                .filePath("src/Foo.java")
                .language("java")
                .build();

        when(indexingService.getFilesByIndex(testIndex.getId()))
                .thenReturn(List.of(fileIndex));

        ResponseEntity<?> response = controller.getFiles(testIndex.getId());

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void searchFiles_returnsResults() {
        when(indexingService.searchFiles(testIndex.getId(), "User"))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.searchFiles(testIndex.getId(), "User");

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getGraph_noNodeId_returnsAllNodes() {
        var graph = new IndexingService.DependencyGraphResponse(List.of(), List.of());
        when(indexingService.getDependencyGraph(testIndex.getId(), null, 3))
                .thenReturn(graph);

        ResponseEntity<?> response = controller.getGraph(testIndex.getId(), null, 3);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getGraph_withNodeId_passesDepth() {
        UUID nodeId = UUID.randomUUID();
        var graph = new IndexingService.DependencyGraphResponse(List.of(), List.of());
        when(indexingService.getDependencyGraph(testIndex.getId(), nodeId, 5))
                .thenReturn(graph);

        ResponseEntity<?> response = controller.getGraph(testIndex.getId(), nodeId, 5);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getGraph_clampsDepthToMax10() {
        // depth clamp happens in the controller: 99 -> 10
        var graph = new IndexingService.DependencyGraphResponse(List.of(), List.of());
        when(indexingService.getDependencyGraph(testIndex.getId(), null, 10))
                .thenReturn(graph);

        ResponseEntity<?> response = controller.getGraph(testIndex.getId(), null, 99);

        assertEquals(200, response.getStatusCode().value());
    }
}
