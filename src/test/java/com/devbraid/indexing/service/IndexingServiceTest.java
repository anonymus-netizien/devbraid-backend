package com.devbraid.indexing.service;

import com.devbraid.indexing.entity.CodebaseIndex;
import com.devbraid.indexing.entity.FileIndex;
import com.devbraid.indexing.repository.CodebaseIndexRepository;
import com.devbraid.indexing.repository.FileIndexRepository;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private CodebaseIndexRepository codebaseIndexRepository;

    @Mock
    private FileIndexRepository fileIndexRepository;

    @InjectMocks
    private IndexingService indexingService;

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
    void createIndex_newIndex_savesAndReturns() {
        when(codebaseIndexRepository.findByUserIdAndRepositoryAndBranch(
                testUser.getId(), "owner/repo", "main"))
                .thenReturn(Optional.empty());
        when(codebaseIndexRepository.save(any(CodebaseIndex.class)))
                .thenReturn(testIndex);

        CodebaseIndex result = indexingService.createIndex(testUser, "owner/repo", "main");

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        verify(codebaseIndexRepository).save(any(CodebaseIndex.class));
    }

    @Test
    void createIndex_existingIndex_returnsExisting() {
        when(codebaseIndexRepository.findByUserIdAndRepositoryAndBranch(
                testUser.getId(), "owner/repo", "main"))
                .thenReturn(Optional.of(testIndex));

        CodebaseIndex result = indexingService.createIndex(testUser, "owner/repo", "main");

        assertNotNull(result);
        assertEquals(testIndex.getId(), result.getId());
        verify(codebaseIndexRepository, never()).save(any());
    }

    @Test
    void getIndex_exists_returnsIndex() {
        when(codebaseIndexRepository.findById(testIndex.getId()))
                .thenReturn(Optional.of(testIndex));

        Optional<CodebaseIndex> result = indexingService.getIndex(testIndex.getId());

        assertTrue(result.isPresent());
        assertEquals("owner/repo", result.get().getRepository());
    }

    @Test
    void getIndex_notExists_returnsEmpty() {
        when(codebaseIndexRepository.findById(any()))
                .thenReturn(Optional.empty());

        Optional<CodebaseIndex> result = indexingService.getIndex(UUID.randomUUID());

        assertFalse(result.isPresent());
    }

    @Test
    void listIndexesByUser_returnsList() {
        when(codebaseIndexRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(List.of(testIndex));

        List<CodebaseIndex> result = indexingService.listIndexesByUser(testUser);

        assertEquals(1, result.size());
    }

    @Test
    void getFilesByIndex_returnsFiles() {
        FileIndex fileIndex = FileIndex.builder()
                .id(UUID.randomUUID())
                .codebaseIndex(testIndex)
                .filePath("src/main/java/Foo.java")
                .language("java")
                .functionCount(5)
                .classCount(1)
                .build();

        when(fileIndexRepository.findByCodebaseIndexIdOrderByFilePath(testIndex.getId()))
                .thenReturn(List.of(fileIndex));

        List<FileIndex> result = indexingService.getFilesByIndex(testIndex.getId());

        assertEquals(1, result.size());
        assertEquals("java", result.get(0).getLanguage());
    }

    @Test
    void searchFiles_returnsMatchingFiles() {
        FileIndex fileIndex = FileIndex.builder()
                .id(UUID.randomUUID())
                .codebaseIndex(testIndex)
                .filePath("src/main/java/UserService.java")
                .language("java")
                .build();

        when(fileIndexRepository.findByIndexIdAndPathPattern(testIndex.getId(), "User"))
                .thenReturn(List.of(fileIndex));

        List<FileIndex> result = indexingService.searchFiles(testIndex.getId(), "User");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getFilePath().contains("User"));
    }
}
