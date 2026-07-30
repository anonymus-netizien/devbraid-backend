package com.devbraid.brief.service;

import com.devbraid.ai.service.AIProvider;
import com.devbraid.ai.service.PromptBuilder;
import com.devbraid.brief.dto.BriefListItemResponse;
import com.devbraid.brief.dto.BriefResponse;
import com.devbraid.brief.entity.ChangeBrief;
import com.devbraid.brief.repository.ChangeBriefRepository;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.ThreadStatus;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("BriefBuilderService Unit Tests")
@ExtendWith(MockitoExtension.class)
class BriefBuilderServiceTest {

    @InjectMocks
    private BriefBuilderService briefBuilderService;

    @Mock
    private ChangeBriefRepository briefRepository;

    @Mock
    private ChangeThreadRepository threadRepository;

    @Mock
    private AIProvider aiProvider;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ModelMapper generalModelMapper;

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
                .status(ThreadStatus.DRAFT)
                .build();

        testBrief = ChangeBrief.builder()
                .id(UUID.randomUUID())
                .thread(testThread)
                .content("AI-generated content")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("generateBrief() creates new brief with AI content")
    void generateBrief_WithAI_SavesAndReturnsBrief() throws Exception {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(promptBuilder.buildBriefPrompt(testThread)).thenReturn("Test prompt");
        when(aiProvider.analyze("Test prompt")).thenReturn("AI-generated content");
        when(briefRepository.findByThreadId(testThread.getId())).thenReturn(Optional.empty());
        when(briefRepository.save(any(ChangeBrief.class))).thenReturn(testBrief);
        when(generalModelMapper.map(any(ChangeBrief.class), eq(BriefResponse.class)))
                .thenAnswer(invocation -> {
                    BriefResponse r = new BriefResponse();
                    r.setId(testBrief.getId());
                    r.setThreadId(testThread.getId());
                    r.setContent("AI-generated content");
                    r.setPublishedToGithub(false);
                    return r;
                });

        BriefResponse response = briefBuilderService.generateBrief(testUser, testThread.getId());

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("AI-generated content");
        assertThat(response.getPublishedToGithub()).isFalse();
        verify(briefRepository).save(any(ChangeBrief.class));
        verify(aiProvider).analyze("Test prompt");
    }

    @Test
    @DisplayName("generateBrief() falls back to template when AI is unavailable")
    void generateBrief_AIFailure_UsesTemplateFallback() throws Exception {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(promptBuilder.buildBriefPrompt(testThread)).thenReturn("Test prompt");
        when(aiProvider.analyze("Test prompt")).thenThrow(new RuntimeException("API unavailable"));
        when(promptBuilder.buildTemplateBrief(testThread)).thenReturn("Template content");
        when(briefRepository.findByThreadId(testThread.getId())).thenReturn(Optional.empty());
        when(briefRepository.save(any(ChangeBrief.class))).thenReturn(testBrief);
        when(generalModelMapper.map(any(ChangeBrief.class), eq(BriefResponse.class)))
                .thenAnswer(invocation -> {
                    BriefResponse r = new BriefResponse();
                    r.setId(testBrief.getId());
                    r.setThreadId(testThread.getId());
                    r.setContent("Template content");
                    return r;
                });

        BriefResponse response = briefBuilderService.generateBrief(testUser, testThread.getId());

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("Template content");
        verify(aiProvider).analyze("Test prompt");
        verify(promptBuilder).buildTemplateBrief(testThread);
    }

    @Test
    @DisplayName("generateBrief() updates existing brief if present")
    void generateBrief_ExistingBrief_UpdatesContent() throws Exception {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(promptBuilder.buildBriefPrompt(testThread)).thenReturn("Test prompt");
        when(aiProvider.analyze("Test prompt")).thenReturn("Updated content");
        when(briefRepository.findByThreadId(testThread.getId())).thenReturn(Optional.of(testBrief));
        when(briefRepository.save(any(ChangeBrief.class))).thenReturn(testBrief);
        when(generalModelMapper.map(any(ChangeBrief.class), eq(BriefResponse.class)))
                .thenAnswer(invocation -> {
                    BriefResponse r = new BriefResponse();
                    r.setId(testBrief.getId());
                    r.setThreadId(testThread.getId());
                    r.setContent("Updated content");
                    return r;
                });

        BriefResponse response = briefBuilderService.generateBrief(testUser, testThread.getId());

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("Updated content");
        verify(briefRepository).save(any(ChangeBrief.class));
    }

    @Test
    @DisplayName("generateBrief() throws when thread not found")
    void generateBrief_ThreadNotFound_ThrowsException() {
        when(threadRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> briefBuilderService.generateBrief(testUser, UUID.randomUUID()))
                .isInstanceOf(ThreadNotFoundException.class)
                .hasMessageContaining("Thread not found");

        verify(briefRepository, never()).save(any());
    }

    @Test
    @DisplayName("listBriefsByUser() returns paginated briefs")
    void listBriefsByUser_ReturnsPaginatedResults() {
        var pageable = PageRequest.of(0, 20);
        var briefPage = new PageImpl<>(List.of(testBrief), pageable, 1);

        when(briefRepository.findAllByUserId(testUser.getId(), pageable)).thenReturn(briefPage);
        when(generalModelMapper.map(any(ChangeBrief.class), eq(BriefListItemResponse.class)))
                .thenAnswer(invocation -> {
                    BriefListItemResponse r = new BriefListItemResponse();
                    r.setId(testBrief.getId());
                    r.setThreadId(testThread.getId());
                    r.setThreadTitle(testThread.getTitle());
                    r.setRepositoryFullName(testThread.getRepositoryFullName());
                    return r;
                });

        Page<BriefListItemResponse> result = briefBuilderService.listBriefsByUser(testUser, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getThreadTitle()).isEqualTo("Test Thread");
    }
}
