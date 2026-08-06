package com.devbraid.brief.service;

import com.devbraid.brief.dto.PublishResponse;
import com.devbraid.brief.entity.ChangeBrief;
import com.devbraid.brief.repository.ChangeBriefRepository;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.ThreadStatus;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.service.GitHubConnectionService;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("BriefPublisherService Unit Tests")
@ExtendWith(MockitoExtension.class)
class BriefPublisherServiceTest {

    @InjectMocks
    private BriefPublisherService briefPublisherService;

    @Mock
    private ChangeBriefRepository briefRepository;

    @Mock
    private ChangeThreadRepository threadRepository;

    @Mock
    private GitHubConnectionService gitHubConnectionService;

    @Mock
    private GitHubApiClient gitHubApiClient;

    private User testUser;
    private ChangeThread testThread;
    private ChangeBrief testBrief;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Test User")
                .email("test@example.com")
                .build();

        testThread = ChangeThread.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Test Thread")
                .repositoryFullName("owner/repo")
                .headBranch("feature")
                .baseBranch("main")
                .status(ThreadStatus.READY)
                .build();

        testBrief = ChangeBrief.builder()
                .id(UUID.randomUUID())
                .thread(testThread)
                .content("Brief content")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("publishToGitHub() publishes brief as PR comment")
    void publishToGitHub_WithValidData_PublishesSuccessfully() {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(briefRepository.findByThreadId(testThread.getId())).thenReturn(Optional.of(testBrief));
        when(gitHubConnectionService.getDecryptedPatForUser(testUser)).thenReturn("ghp_testToken");
        when(briefRepository.save(any(ChangeBrief.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(threadRepository.save(any(ChangeThread.class))).thenReturn(testThread);

        PublishResponse response = briefPublisherService.publishToGitHub(testUser, testThread.getId(), 42);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("PR #42");
        assertThat(response.getPublishUrl()).contains("pull/42");
        assertThat(response.getPublishedAt()).isNotNull();

        verify(gitHubApiClient).createPullRequestComment(
                eq("ghp_testToken"), eq("owner"), eq("repo"), eq(42), eq("Brief content")
        );
        verify(briefRepository).save(any(ChangeBrief.class));
        verify(threadRepository).save(any(ChangeThread.class));
    }

    @Test
    @DisplayName("publishToGitHub() throws when thread not found")
    void publishToGitHub_ThreadNotFound_ThrowsException() {
        when(threadRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> briefPublisherService.publishToGitHub(testUser, UUID.randomUUID(), 1))
                .isInstanceOf(ThreadNotFoundException.class)
                .hasMessageContaining("Thread not found");

        verify(briefRepository, never()).findByThreadId(any());
    }

    @Test
    @DisplayName("publishToGitHub() throws when no brief exists")
    void publishToGitHub_NoBrief_ThrowsException() {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(briefRepository.findByThreadId(testThread.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> briefPublisherService.publishToGitHub(testUser, testThread.getId(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No brief generated yet");
    }

    @Test
    @DisplayName("publishToGitHub() throws when GitHub connection not found")
    void publishToGitHub_NotConnected_ThrowsException() {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(briefRepository.findByThreadId(testThread.getId())).thenReturn(Optional.of(testBrief));
        when(gitHubConnectionService.getDecryptedPatForUser(testUser))
                .thenThrow(new com.devbraid.github.exception.GitHubNotConnectedException("Connect GitHub first"));

        assertThatThrownBy(() -> briefPublisherService.publishToGitHub(testUser, testThread.getId(), 1))
                .isInstanceOf(com.devbraid.github.exception.GitHubNotConnectedException.class)
                .hasMessageContaining("Connect GitHub first");
    }

    @Test
    @DisplayName("getPublishStatus() returns published status when brief is published")
    void getPublishStatus_Published_ReturnsSuccess() {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));

        ChangeBrief publishedBrief = ChangeBrief.builder()
                .id(testBrief.getId())
                .thread(testThread)
                .content("Content")
                .publishedAt(OffsetDateTime.now())
                .publishUrl("https://github.com/owner/repo/pull/42")
                .build();

        when(briefRepository.findByThreadId(testThread.getId())).thenReturn(Optional.of(publishedBrief));

        PublishResponse response = briefPublisherService.getPublishStatus(testUser, testThread.getId());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getPublishUrl()).contains("github.com");
        assertThat(response.getMessage()).isEqualTo("Brief published");
    }

    @Test
    @DisplayName("getPublishStatus() returns not published when brief exists but not published")
    void getPublishStatus_NotPublished_ReturnsNotPublished() {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(briefRepository.findByThreadId(testThread.getId())).thenReturn(Optional.of(testBrief));

        PublishResponse response = briefPublisherService.getPublishStatus(testUser, testThread.getId());

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Brief not yet published");
    }

    @Test
    @DisplayName("getPublishStatus() returns not published when no brief exists")
    void getPublishStatus_NoBrief_ReturnsNotPublished() {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(briefRepository.findByThreadId(testThread.getId())).thenReturn(Optional.empty());

        PublishResponse response = briefPublisherService.getPublishStatus(testUser, testThread.getId());

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Brief not yet published");
    }

    @Test
    @DisplayName("getPublishStatus() throws when thread not found")
    void getPublishStatus_ThreadNotFound_ThrowsException() {
        when(threadRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> briefPublisherService.getPublishStatus(testUser, UUID.randomUUID()))
                .isInstanceOf(ThreadNotFoundException.class)
                .hasMessageContaining("Thread not found");

        verify(briefRepository, never()).findByThreadId(any());
    }
}
