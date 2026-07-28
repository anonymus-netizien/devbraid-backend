package com.devbraid.changethread;

import com.devbraid.analysis.service.RiskAnalysisService;
import com.devbraid.changethread.dto.request.CreateThreadRequest;
import com.devbraid.changethread.dto.response.ThreadResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.ThreadStatus;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.repository.DecisionNoteRepository;
import com.devbraid.changethread.service.ChangeThreadService;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.response.ChangedFileDto;
import com.devbraid.github.dto.response.CommitSummaryDto;
import com.devbraid.github.dto.response.GitHubCompareResponse;
import com.devbraid.github.entity.GitHubConnection;
import com.devbraid.github.exception.GitHubTokenInvalidException;
import com.devbraid.github.repository.GitHubConnectionRepository;
import com.devbraid.github.util.PatEncryptor;
import com.devbraid.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChangeThreadService — no Spring context required.
 * Uses Mockito to mock all dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeThreadService Unit Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChangeThreadServiceTest {

    @InjectMocks
    private ChangeThreadService threadService;

    @Mock
    private ChangeThreadRepository threadRepository;

    @Mock
    private DecisionNoteRepository noteRepository;

    @Mock
    private GitHubConnectionRepository connectionRepository;

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private PatEncryptor patEncryptor;

    @Mock
    private RiskAnalysisService riskAnalysisService;

    private User testUser;
    private GitHubConnection testConnection;

    @BeforeEach
    void setUp() {
        testUser = mock(User.class);
        when(testUser.getId()).thenReturn(UUID.randomUUID());
        when(testUser.getEmail()).thenReturn("test@example.com");

        testConnection = GitHubConnection.builder()
                .user(testUser)
                .encryptedPat(new byte[]{1, 2, 3})
                .iv(new byte[]{4, 5, 6})
                .githubUsername("testuser")
                .connectedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("createThread() persists thread with GitHub compare data")
    void createThread_WithData_PersistsThread() {
        when(connectionRepository.findByUserId(any())).thenReturn(Optional.of(testConnection));
        when(patEncryptor.decrypt(any(), any())).thenReturn("ghp_testToken123");

        var compareResult = new GitHubCompareResponse();
        compareResult.setStatus("diverged");
        compareResult.setAheadBy(2);
        compareResult.setBehindBy(0);
        compareResult.setTotalCommits(2);
        compareResult.setCommits(java.util.List.of(
                new CommitSummaryDto("abc123", "feat: add feature", null),
                new CommitSummaryDto("def456", "fix: resolve bug", null)
        ));
        compareResult.setFiles(java.util.List.of(
                new ChangedFileDto("src/main.java", "modified", 10, 2),
                new ChangedFileDto("src/test.java", "added", 50, 0)
        ));

        when(gitHubApiClient.compare(any(), eq("test-owner"), eq("test-repo"), eq("main"), eq("feature/test")))
                .thenReturn(compareResult);
        when(noteRepository.findByThreadIdOrderByCreatedAtDesc(any())).thenReturn(java.util.List.of());

        // Mock save to return the entity with an ID
        when(threadRepository.save(any(ChangeThread.class))).thenAnswer(invocation -> {
            ChangeThread thread = invocation.getArgument(0);
            var field = ChangeThread.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(thread, UUID.randomUUID());
            var createdAt = ChangeThread.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(thread, OffsetDateTime.now());
            var updatedAt = ChangeThread.class.getDeclaredField("updatedAt");
            updatedAt.setAccessible(true);
            updatedAt.set(thread, OffsetDateTime.now());
            return thread;
        });

        CreateThreadRequest request = new CreateThreadRequest(
                "test-owner/test-repo", "feature/test", "main", "Test Thread", "A test thread"
        );

        ThreadResponse response = threadService.createThread(testUser, request);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Test Thread");
        assertThat(response.getRepositoryFullName()).isEqualTo("test-owner/test-repo");
        assertThat(response.getHeadBranch()).isEqualTo("feature/test");
        assertThat(response.getBaseBranch()).isEqualTo("main");
        assertThat(response.getStatus()).isEqualTo(ThreadStatus.DRAFT);
        assertThat(response.getCommits()).isNotNull();
        assertThat(response.getChangedFiles()).isNotNull();
        assertThat(response.getCommitSha()).isEqualTo("def456");

        verify(gitHubApiClient).compare(any(), eq("test-owner"), eq("test-repo"), eq("main"), eq("feature/test"));
        verify(threadRepository).save(any(ChangeThread.class));
    }

    @Test
    @Order(2)
    @DisplayName("createThread() throws GitHubTokenInvalidException for invalid token")
    void createThread_InvalidToken_ThrowsException() {
        when(connectionRepository.findByUserId(any())).thenReturn(Optional.of(testConnection));
        when(patEncryptor.decrypt(any(), any())).thenReturn("ghp_bad_token");
        when(gitHubApiClient.compare(any(), any(), any(), any(), any()))
                .thenThrow(new GitHubTokenInvalidException("Invalid token"));

        CreateThreadRequest request = new CreateThreadRequest(
                "test-owner/test-repo", "feature/bad", "main", "Bad Token Test", null
        );

        assertThatThrownBy(() -> threadService.createThread(testUser, request))
                .isInstanceOf(GitHubTokenInvalidException.class)
                .hasMessageContaining("Invalid token");

        verify(threadRepository, never()).save(any());
    }

    @Test
    @Order(3)
    @DisplayName("createThread() creates thread with null diff when GitHub returns 500")
    void createThread_GitHubError_CreatesThreadWithNullDiff() {
        when(connectionRepository.findByUserId(any())).thenReturn(Optional.of(testConnection));
        when(patEncryptor.decrypt(any(), any())).thenReturn("ghp_testToken123");
        when(gitHubApiClient.compare(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("GitHub API error: 500"));
        when(noteRepository.findByThreadIdOrderByCreatedAtDesc(any())).thenReturn(java.util.List.of());

        when(threadRepository.save(any(ChangeThread.class))).thenAnswer(invocation -> {
            ChangeThread thread = invocation.getArgument(0);
            var field = ChangeThread.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(thread, UUID.randomUUID());
            var createdAt = ChangeThread.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(thread, OffsetDateTime.now());
            var updatedAt = ChangeThread.class.getDeclaredField("updatedAt");
            updatedAt.setAccessible(true);
            updatedAt.set(thread, OffsetDateTime.now());
            return thread;
        });

        CreateThreadRequest request = new CreateThreadRequest(
                "test-owner/server-error", "feature/error", "main", "Error Test", null
        );

        ThreadResponse response = threadService.createThread(testUser, request);

        assertThat(response).isNotNull();
        assertThat(response.getCommits()).isNull();
        assertThat(response.getChangedFiles()).isNull();
    }

    @Test
    @Order(4)
    @DisplayName("createThread() throws GitHubNotConnectedException when not connected")
    void createThread_NotConnected_ThrowsException() {
        when(connectionRepository.findByUserId(any())).thenReturn(Optional.empty());

        CreateThreadRequest request = new CreateThreadRequest(
                "test-owner/test-repo", "feature/test", "main", "No Connection", null
        );

        assertThatThrownBy(() -> threadService.createThread(testUser, request))
                .isInstanceOf(com.devbraid.github.exception.GitHubNotConnectedException.class)
                .hasMessageContaining("Connect GitHub first");

        verify(threadRepository, never()).save(any());
    }
}
