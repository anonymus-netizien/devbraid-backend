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
import com.devbraid.github.entity.GitHubConnection;
import com.devbraid.github.repository.GitHubConnectionRepository;
import com.devbraid.github.util.PatEncryptor;
import com.devbraid.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ChangeThread Tests")
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChangeThreadIntegrationTest {

    private static final int WIREMOCK_PORT = 8098;
    private static WireMockServer wireMockServer;
    private static User testUser;
    private static GitHubConnection testConnection;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

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

    @InjectMocks
    private ChangeThreadService threadService;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WIREMOCK_PORT);
        wireMockServer.start();
        WireMock.configureFor("localhost", WIREMOCK_PORT);

        testUser = mock(User.class);
        lenient().when(testUser.getId()).thenReturn(UUID.randomUUID());
        lenient().when(testUser.getEmail()).thenReturn("test@example.com");

        testConnection = GitHubConnection.builder()
                .user(testUser)
                .encryptedPat(new byte[]{1, 2, 3})
                .iv(new byte[]{4, 5, 6})
                .githubUsername("testuser")
                .connectedAt(OffsetDateTime.now())
                .build();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        resetAllRequests();
        reset(threadRepository, noteRepository, connectionRepository, gitHubApiClient, patEncryptor, riskAnalysisService);
        lenient().when(connectionRepository.findByUserId(any())).thenReturn(Optional.of(testConnection));
        lenient().when(patEncryptor.decrypt(any(), any())).thenReturn("ghp_testToken123456");
        lenient().when(noteRepository.findByThreadIdOrderByCreatedAtDesc(any())).thenReturn(java.util.List.of());
    }

    @Test
    @Order(1)
    @DisplayName("createThread() persists thread with GitHub data")
    void createThread_WithData_PersistsThread() throws Exception {
        var compareResult = new com.devbraid.github.dto.response.GitHubCompareResponse();
        compareResult.setStatus("diverged");
        compareResult.setAheadBy(2);
        compareResult.setBehindBy(0);
        compareResult.setTotalCommits(2);
        compareResult.setCommits(java.util.List.of(
                new com.devbraid.github.dto.response.CommitSummaryDto("abc123", "feat: add feature", null),
                new com.devbraid.github.dto.response.CommitSummaryDto("def456", "fix: resolve bug", null)
        ));
        compareResult.setFiles(java.util.List.of(
                new com.devbraid.github.dto.response.ChangedFileDto("src/main.java", "modified", 10, 2),
                new com.devbraid.github.dto.response.ChangedFileDto("src/test.java", "added", 50, 0)
        ));

        when(gitHubApiClient.compare(any(), any(), any(), any(), any())).thenReturn(compareResult);

        when(threadRepository.save(any(ChangeThread.class))).thenAnswer(invocation -> {
            ChangeThread thread = invocation.getArgument(0);
            try {
                var field = ChangeThread.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(thread, UUID.randomUUID());
                var createdAt = ChangeThread.class.getDeclaredField("createdAt");
                createdAt.setAccessible(true);
                createdAt.set(thread, OffsetDateTime.now());
                var updatedAt = ChangeThread.class.getDeclaredField("updatedAt");
                updatedAt.setAccessible(true);
                updatedAt.set(thread, OffsetDateTime.now());
            } catch (Exception e) {
                // Ignore
            }
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
    @DisplayName("createThread() throws when GitHub token is invalid")
    void createThread_InvalidToken_ThrowsException() throws Exception {
        when(gitHubApiClient.compare(any(), any(), any(), any(), any()))
                .thenThrow(new com.devbraid.github.exception.GitHubTokenInvalidException("Invalid token"));

        CreateThreadRequest request = new CreateThreadRequest(
                "test-owner/test-repo", "feature/bad", "main", "Bad Token Test", null
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                com.devbraid.github.exception.GitHubTokenInvalidException.class,
                () -> threadService.createThread(testUser, request)
        );
    }

    @Test
    @Order(3)
    @DisplayName("createThread() propagates exception when GitHub returns 500")
    void createThread_GitHubError_PropagatesException() throws Exception {
        when(gitHubApiClient.compare(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("GitHub API error: 500"));

        CreateThreadRequest request = new CreateThreadRequest(
                "test-owner/server-error", "feature/error", "main", "Error Test", null
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> threadService.createThread(testUser, request)
        );
    }

}
