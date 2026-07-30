package com.devbraid.changethread.service;

import com.devbraid.changethread.dto.request.CreateNoteRequest;
import com.devbraid.changethread.dto.request.UpdateNoteRequest;
import com.devbraid.changethread.dto.response.NoteListItemResponse;
import com.devbraid.changethread.dto.response.NoteResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.DecisionNote;
import com.devbraid.changethread.entity.NoteContext;
import com.devbraid.changethread.entity.NoteStatus;
import com.devbraid.changethread.exception.NoteNotFoundException;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.repository.DecisionNoteRepository;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
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

@DisplayName("DecisionNoteService Unit Tests")
@ExtendWith(MockitoExtension.class)
class DecisionNoteServiceTest {

    @InjectMocks
    private DecisionNoteService decisionNoteService;

    @Mock
    private DecisionNoteRepository noteRepository;

    @Mock
    private ChangeThreadRepository threadRepository;

    @Mock
    private ModelMapper generalModelMapper;

    private User testUser;
    private ChangeThread testThread;
    private DecisionNote testNote;

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
                .build();

        testNote = DecisionNote.builder()
                .id(UUID.randomUUID())
                .thread(testThread)
                .author(testUser)
                .context(NoteContext.THREAD)
                .decision("Test decision")
                .rationale("Test rationale")
                .alternatives("Alternative A")
                .impact("Low impact")
                .status(NoteStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createNote() persists and returns note")
    void createNote_WithValidData_ReturnsNoteResponse() {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(noteRepository.save(any(DecisionNote.class))).thenReturn(testNote);
        when(generalModelMapper.map(any(DecisionNote.class), eq(NoteResponse.class)))
                .thenAnswer(invocation -> {
                    NoteResponse r = new NoteResponse();
                    r.setId(testNote.getId());
                    r.setDecision(testNote.getDecision());
                    r.setRationale(testNote.getRationale());
                    return r;
                });

        CreateNoteRequest request = new CreateNoteRequest(
                NoteContext.THREAD, null, "Test decision", "Test rationale", "Alternative A", "Low impact"
        );

        NoteResponse response = decisionNoteService.createNote(testUser, testThread.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testNote.getId());
        assertThat(response.getDecision()).isEqualTo("Test decision");
        verify(noteRepository).save(any(DecisionNote.class));
    }

    @Test
    @DisplayName("createNote() throws ThreadNotFoundException when thread not found")
    void createNote_ThreadNotFound_ThrowsException() {
        when(threadRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        CreateNoteRequest request = new CreateNoteRequest(
                NoteContext.THREAD, null, "Decision", "Rationale", null, null
        );

        assertThatThrownBy(() -> decisionNoteService.createNote(testUser, UUID.randomUUID(), request))
                .isInstanceOf(ThreadNotFoundException.class)
                .hasMessageContaining("Thread not found");

        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("listNotes() returns notes for authorized user")
    void listNotes_WithValidUser_ReturnsNotes() {
        when(threadRepository.findByIdAndUserId(testThread.getId(), testUser.getId()))
                .thenReturn(Optional.of(testThread));
        when(noteRepository.findByThreadIdOrderByCreatedAtDesc(testThread.getId()))
                .thenReturn(List.of(testNote));
        when(generalModelMapper.map(any(DecisionNote.class), eq(NoteResponse.class)))
                .thenAnswer(invocation -> {
                    NoteResponse r = new NoteResponse();
                    r.setId(testNote.getId());
                    r.setDecision(testNote.getDecision());
                    r.setThreadId(testThread.getId());
                    r.setAuthorId(testUser.getId());
                    return r;
                });

        List<NoteResponse> notes = decisionNoteService.listNotes(testThread.getId(), testUser);

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getDecision()).isEqualTo("Test decision");
        verify(threadRepository).findByIdAndUserId(testThread.getId(), testUser.getId());
    }

    @Test
    @DisplayName("listNotes() throws ThreadNotFoundException when user not authorized")
    void listNotes_ThreadNotFound_ThrowsException() {
        when(threadRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> decisionNoteService.listNotes(UUID.randomUUID(), testUser))
                .isInstanceOf(ThreadNotFoundException.class)
                .hasMessageContaining("Thread not found");

        verify(noteRepository, never()).findByThreadIdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("listAllNotes() returns paginated notes for user")
    void listAllNotes_ReturnsPaginatedResults() {
        var pageable = PageRequest.of(0, 20);
        var notePage = new PageImpl<>(List.of(testNote), pageable, 1);

        when(noteRepository.findAllByUserId(testUser.getId(), pageable)).thenReturn(notePage);
        when(generalModelMapper.map(any(DecisionNote.class), eq(NoteListItemResponse.class)))
                .thenAnswer(invocation -> {
                    NoteListItemResponse r = new NoteListItemResponse();
                    r.setId(testNote.getId());
                    r.setDecision(testNote.getDecision());
                    r.setThreadId(testThread.getId());
                    r.setThreadTitle(testThread.getTitle());
                    return r;
                });

        Page<NoteListItemResponse> result = decisionNoteService.listAllNotes(testUser, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDecision()).isEqualTo("Test decision");
        assertThat(result.getContent().get(0).getThreadTitle()).isEqualTo("Test Thread");
    }

    @Test
    @DisplayName("updateNote() updates fields when user is the author")
    void updateNote_AsAuthor_UpdatesFields() {
        when(noteRepository.findById(testNote.getId())).thenReturn(Optional.of(testNote));
        when(noteRepository.save(any(DecisionNote.class))).thenReturn(testNote);
        when(generalModelMapper.map(any(DecisionNote.class), eq(NoteResponse.class)))
                .thenAnswer(invocation -> {
                    NoteResponse r = new NoteResponse();
                    r.setId(testNote.getId());
                    r.setDecision("Updated decision");
                    r.setRationale("Updated rationale");
                    return r;
                });

        UpdateNoteRequest request = new UpdateNoteRequest("Updated decision", "Updated rationale", null, null);
        NoteResponse response = decisionNoteService.updateNote(testUser, testNote.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.getDecision()).isEqualTo("Updated decision");
        assertThat(response.getRationale()).isEqualTo("Updated rationale");
        verify(noteRepository).save(any(DecisionNote.class));
    }

    @Test
    @DisplayName("updateNote() throws when note not found")
    void updateNote_NotFound_ThrowsException() {
        when(noteRepository.findById(any())).thenReturn(Optional.empty());

        UpdateNoteRequest request = new UpdateNoteRequest("Updated", "Updated", null, null);

        assertThatThrownBy(() -> decisionNoteService.updateNote(testUser, UUID.randomUUID(), request))
                .isInstanceOf(NoteNotFoundException.class)
                .hasMessageContaining("Decision note not found");
    }

    @Test
    @DisplayName("updateNote() throws when user is not the author")
    void updateNote_NotAuthor_ThrowsAccessDenied() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        when(noteRepository.findById(testNote.getId())).thenReturn(Optional.of(testNote));

        UpdateNoteRequest request = new UpdateNoteRequest("Updated", "Updated", null, null);

        assertThatThrownBy(() -> decisionNoteService.updateNote(otherUser, testNote.getId(), request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    @DisplayName("deleteNote() deletes when user is the author")
    void deleteNote_AsAuthor_DeletesNote() {
        when(noteRepository.findById(testNote.getId())).thenReturn(Optional.of(testNote));

        decisionNoteService.deleteNote(testUser, testNote.getId());

        verify(noteRepository).delete(testNote);
    }

    @Test
    @DisplayName("deleteNote() throws when user is not the author")
    void deleteNote_NotAuthor_ThrowsAccessDenied() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        when(noteRepository.findById(testNote.getId())).thenReturn(Optional.of(testNote));

        assertThatThrownBy(() -> decisionNoteService.deleteNote(otherUser, testNote.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Not authorized");

        verify(noteRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteNote() throws when note not found")
    void deleteNote_NotFound_ThrowsException() {
        when(noteRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> decisionNoteService.deleteNote(testUser, UUID.randomUUID()))
                .isInstanceOf(NoteNotFoundException.class)
                .hasMessageContaining("Decision note not found");
    }
}
