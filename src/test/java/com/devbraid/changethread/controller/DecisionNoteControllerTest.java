package com.devbraid.changethread.controller;

import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.changethread.dto.request.CreateNoteRequest;
import com.devbraid.changethread.dto.request.UpdateNoteRequest;
import com.devbraid.changethread.dto.response.NoteListItemResponse;
import com.devbraid.changethread.dto.response.NoteResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.NoteContext;
import com.devbraid.changethread.entity.NoteStatus;
import com.devbraid.changethread.entity.ThreadStatus;
import com.devbraid.changethread.exception.NoteNotFoundException;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.service.DecisionNoteService;
import com.devbraid.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("DecisionNoteController Unit Tests")
@ExtendWith(MockitoExtension.class)
class DecisionNoteControllerTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID THREAD_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    private static final UUID NOTE_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");
    private static final String DECISION = "Switched to RSA-SHA256 for token hashing";
    private static final String RATIONALE = "Better security for JWT tokens";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    @Mock
    private DecisionNoteService decisionNoteService;
    @Mock
    private ChangeThreadRepository threadRepository;
    private DecisionNoteController controller;
    private User testUser;

    @BeforeEach
    void setUp() {
        controller = new DecisionNoteController(decisionNoteService, threadRepository);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver()
                )
                .build();

        testUser = User.builder()
                .id(USER_ID)
                .fullName("Test User")
                .email("test@example.com")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(testUser, null, "ROLE_DEVELOPER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private NoteResponse buildNoteResponse() {
        return NoteResponse.builder()
                .id(NOTE_ID)
                .threadId(THREAD_ID)
                .authorId(USER_ID)
                .context(NoteContext.THREAD)
                .decision(DECISION)
                .rationale(RATIONALE)
                .alternatives("HMAC-SHA512")
                .impact("Security upgrade")
                .status(NoteStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private ChangeThread buildThread() {
        ChangeThread thread = mock(ChangeThread.class);
        lenient().when(thread.getId()).thenReturn(THREAD_ID);
        lenient().when(thread.getTitle()).thenReturn("Test Thread");
        lenient().when(thread.getRepositoryFullName()).thenReturn("owner/repo");
        lenient().when(thread.getHeadBranch()).thenReturn("feature-branch");
        lenient().when(thread.getBaseBranch()).thenReturn("main");
        lenient().when(thread.getStatus()).thenReturn(ThreadStatus.DRAFT);
        return thread;
    }

    @Test
    @DisplayName("POST /api/v1/threads/{threadId}/notes returns 201 CREATED")
    void createNote_Returns201() throws Exception {
        NoteResponse response = buildNoteResponse();
        when(decisionNoteService.createNote(any(User.class), eq(THREAD_ID), any(CreateNoteRequest.class)))
                .thenReturn(response);

        String body = objectMapper.writeValueAsString(
                new CreateNoteRequest(NoteContext.THREAD, null, DECISION, RATIONALE, "HMAC-SHA512", "Security upgrade"));

        mockMvc.perform(post("/api/v1/threads/{threadId}/notes", THREAD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Note created"))
                .andExpect(jsonPath("$.data.id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$.data.decision").value(DECISION))
                .andExpect(jsonPath("$.data.rationale").value(RATIONALE))
                .andExpect(jsonPath("$.data.alternatives").value("HMAC-SHA512"))
                .andExpect(jsonPath("$.data.impact").value("Security upgrade"))
                .andExpect(jsonPath("$.data.context").value("THREAD"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.threadId").value(THREAD_ID.toString()))
                .andExpect(jsonPath("$.data.authorId").value(USER_ID.toString()));

        verify(decisionNoteService).createNote(any(User.class), eq(THREAD_ID), any(CreateNoteRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/threads/{threadId}/notes returns 400 for missing required fields")
    void createNote_MissingFields_Returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateNoteRequest(null, null, "", "", null, null));

        mockMvc.perform(post("/api/v1/threads/{threadId}/notes", THREAD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/threads/{threadId}/notes returns 404 when thread not found")
    void createNote_ThreadNotFound_Returns404() throws Exception {
        when(decisionNoteService.createNote(any(User.class), eq(THREAD_ID), any(CreateNoteRequest.class)))
                .thenThrow(new ThreadNotFoundException("Thread not found"));

        String body = objectMapper.writeValueAsString(
                new CreateNoteRequest(NoteContext.THREAD, null, DECISION, RATIONALE, null, null));

        mockMvc.perform(post("/api/v1/threads/{threadId}/notes", THREAD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Thread not found"));
    }

    @Test
    @DisplayName("GET /api/v1/threads/{threadId}/notes returns 200 OK with notes list")
    void listThreadNotes_Returns200() throws Exception {
        ChangeThread thread = buildThread();
        when(threadRepository.findByIdAndUserId(THREAD_ID, USER_ID)).thenReturn(Optional.of(thread));

        NoteResponse note = buildNoteResponse();
        when(decisionNoteService.listNotes(THREAD_ID)).thenReturn(List.of(note));

        mockMvc.perform(get("/api/v1/threads/{threadId}/notes", THREAD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notes retrieved"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$.data[0].decision").value(DECISION))
                .andExpect(jsonPath("$.data[0].rationale").value(RATIONALE));

        verify(threadRepository).findByIdAndUserId(THREAD_ID, USER_ID);
        verify(decisionNoteService).listNotes(THREAD_ID);
    }

    @Test
    @DisplayName("GET /api/v1/threads/{threadId}/notes returns 404 when thread not found")
    void listThreadNotes_ThreadNotFound_Returns404() throws Exception {
        when(threadRepository.findByIdAndUserId(THREAD_ID, USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/threads/{threadId}/notes", THREAD_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Thread not found"));

        verify(threadRepository).findByIdAndUserId(THREAD_ID, USER_ID);
        verify(decisionNoteService, never()).listNotes(any());
    }

    @Test
    @DisplayName("PUT /api/v1/threads/{threadId}/notes/{noteId} returns 200 OK")
    void updateNote_Returns200() throws Exception {
        NoteResponse response = NoteResponse.builder()
                .id(NOTE_ID)
                .threadId(THREAD_ID)
                .authorId(USER_ID)
                .decision("Updated decision")
                .rationale("Updated rationale")
                .status(NoteStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();

        when(decisionNoteService.updateNote(any(User.class), eq(NOTE_ID), any(UpdateNoteRequest.class)))
                .thenReturn(response);

        String body = objectMapper.writeValueAsString(new UpdateNoteRequest("Updated decision", "Updated rationale", null, null));

        mockMvc.perform(put("/api/v1/threads/{threadId}/notes/{noteId}", THREAD_ID, NOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Note updated"))
                .andExpect(jsonPath("$.data.id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$.data.decision").value("Updated decision"))
                .andExpect(jsonPath("$.data.rationale").value("Updated rationale"));

        verify(decisionNoteService).updateNote(any(User.class), eq(NOTE_ID), any(UpdateNoteRequest.class));
    }

    @Test
    @DisplayName("PUT /api/v1/threads/{threadId}/notes/{noteId} returns 403 when not author")
    void updateNote_Unauthorized_Returns403() throws Exception {
        when(decisionNoteService.updateNote(any(User.class), eq(NOTE_ID), any(UpdateNoteRequest.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Not authorized to update this note"));

        String body = objectMapper.writeValueAsString(new UpdateNoteRequest("Updated", "Updated rationale", null, null));

        mockMvc.perform(put("/api/v1/threads/{threadId}/notes/{noteId}", THREAD_ID, NOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/threads/{threadId}/notes/{noteId} returns 200 OK")
    void deleteNote_Returns200() throws Exception {
        doNothing().when(decisionNoteService).deleteNote(any(User.class), eq(NOTE_ID));

        mockMvc.perform(delete("/api/v1/threads/{threadId}/notes/{noteId}", THREAD_ID, NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Note deleted"));

        verify(decisionNoteService).deleteNote(any(User.class), eq(NOTE_ID));
    }

    @Test
    @DisplayName("DELETE /api/v1/threads/{threadId}/notes/{noteId} returns 404 when note not found")
    void deleteNote_NotFound_Returns404() throws Exception {
        doThrow(new NoteNotFoundException("Decision note not found"))
                .when(decisionNoteService).deleteNote(any(User.class), eq(NOTE_ID));

        mockMvc.perform(delete("/api/v1/threads/{threadId}/notes/{noteId}", THREAD_ID, NOTE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Decision note not found"));
    }

    @Test
    @DisplayName("GET /api/v1/notes returns 200 OK with paginated notes")
    void listAllNotes_Returns200() throws Exception {
        NoteListItemResponse item = NoteListItemResponse.builder()
                .id(NOTE_ID)
                .threadId(THREAD_ID)
                .threadTitle("Test Thread")
                .repositoryFullName("owner/repo")
                .decision(DECISION)
                .rationale(RATIONALE)
                .status(NoteStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();

        var page = new PageImpl<NoteListItemResponse>(List.of(item), PageRequest.of(0, 20), 1);
        when(decisionNoteService.listAllNotes(any(User.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/notes")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notes retrieved"))
                .andExpect(jsonPath("$.data.content[0].id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].decision").value(DECISION))
                .andExpect(jsonPath("$.data.content[0].rationale").value(RATIONALE))
                .andExpect(jsonPath("$.data.content[0].threadTitle").value("Test Thread"))
                .andExpect(jsonPath("$.data.content[0].repositoryFullName").value("owner/repo"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(decisionNoteService).listAllNotes(any(User.class), any());
    }

    @Test
    @DisplayName("GET /api/v1/notes returns 200 OK with empty page")
    void listAllNotes_Empty_Returns200() throws Exception {
        var emptyPage = new PageImpl<NoteListItemResponse>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(decisionNoteService.listAllNotes(any(User.class), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/notes")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        verify(decisionNoteService).listAllNotes(any(User.class), any());
    }
}
