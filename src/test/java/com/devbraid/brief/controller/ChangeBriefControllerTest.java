package com.devbraid.brief.controller;

import com.devbraid.brief.dto.BriefListItemResponse;
import com.devbraid.brief.dto.BriefResponse;
import com.devbraid.brief.service.BriefBuilderService;
import com.devbraid.changethread.entity.ThreadStatus;
import com.devbraid.changethread.exception.BriefNotFoundException;
import com.devbraid.common.exception.GlobalExceptionHandler;
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ChangeBriefController Unit Tests")
@ExtendWith(MockitoExtension.class)
class ChangeBriefControllerTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID THREAD_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    private static final UUID BRIEF_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");
    private static final String THREAD_TITLE = "JWT Token Hashing Upgrade";
    private static final String REPO_FULL_NAME = "owner/repo";
    private static final String CONTENT = "# Change Brief\n\nThis change upgrades the token hashing algorithm.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    @Mock
    private BriefBuilderService briefBuilderService;
    private ChangeBriefController controller;
    private User testUser;

    @BeforeEach
    void setUp() {
        controller = new ChangeBriefController(briefBuilderService);
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

    private BriefResponse buildBriefResponse() {
        return BriefResponse.builder()
                .id(BRIEF_ID)
                .threadId(THREAD_ID)
                .content(CONTENT)
                .publishedToGithub(false)
                .publishUrl(null)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private BriefListItemResponse buildBriefListItem() {
        return BriefListItemResponse.builder()
                .id(BRIEF_ID)
                .threadId(THREAD_ID)
                .threadTitle(THREAD_TITLE)
                .repositoryFullName(REPO_FULL_NAME)
                .headBranch("feature-branch")
                .baseBranch("main")
                .threadStatus(ThreadStatus.READY)
                .publishedToGithub(false)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/briefs returns 200 OK with paginated briefs")
    void listBriefs_Returns200() throws Exception {
        BriefListItemResponse item = buildBriefListItem();
        var page = new PageImpl<BriefListItemResponse>(List.of(item), PageRequest.of(0, 20), 1);
        when(briefBuilderService.listBriefsByUser(any(User.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/briefs")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Briefs retrieved"))
                .andExpect(jsonPath("$.data.content[0].id").value(BRIEF_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].threadId").value(THREAD_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].threadTitle").value(THREAD_TITLE))
                .andExpect(jsonPath("$.data.content[0].repositoryFullName").value(REPO_FULL_NAME))
                .andExpect(jsonPath("$.data.content[0].headBranch").value("feature-branch"))
                .andExpect(jsonPath("$.data.content[0].baseBranch").value("main"))
                .andExpect(jsonPath("$.data.content[0].threadStatus").value("READY"))
                .andExpect(jsonPath("$.data.content[0].publishedToGithub").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(briefBuilderService).listBriefsByUser(any(User.class), any());
    }

    @Test
    @DisplayName("GET /api/v1/briefs returns 200 OK with empty page")
    void listBriefs_Empty_Returns200() throws Exception {
        var emptyPage = new PageImpl<BriefListItemResponse>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(briefBuilderService.listBriefsByUser(any(User.class), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/briefs")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        verify(briefBuilderService).listBriefsByUser(any(User.class), any());
    }

    @Test
    @DisplayName("GET /api/v1/briefs/{id} returns 200 OK with brief detail")
    void getBrief_Returns200() throws Exception {
        BriefResponse response = buildBriefResponse();
        when(briefBuilderService.getBriefById(any(User.class), eq(BRIEF_ID))).thenReturn(response);

        mockMvc.perform(get("/api/v1/briefs/{id}", BRIEF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Brief retrieved"))
                .andExpect(jsonPath("$.data.id").value(BRIEF_ID.toString()))
                .andExpect(jsonPath("$.data.threadId").value(THREAD_ID.toString()))
                .andExpect(jsonPath("$.data.content").value(CONTENT))
                .andExpect(jsonPath("$.data.publishedToGithub").value(false))
                .andExpect(jsonPath("$.data.createdAt").exists());

        verify(briefBuilderService).getBriefById(any(User.class), eq(BRIEF_ID));
    }

    @Test
    @DisplayName("GET /api/v1/briefs/{id} returns 404 when brief not found")
    void getBrief_NotFound_Returns404() throws Exception {
        when(briefBuilderService.getBriefById(any(User.class), eq(BRIEF_ID)))
                .thenThrow(new BriefNotFoundException("Brief not found"));

        mockMvc.perform(get("/api/v1/briefs/{id}", BRIEF_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Brief not found"));

        verify(briefBuilderService).getBriefById(any(User.class), eq(BRIEF_ID));
    }

    @Test
    @DisplayName("GET /api/v1/briefs/{id} returns 404 when brief belongs to different user")
    void getBrief_Unauthorized_Returns404() throws Exception {
        when(briefBuilderService.getBriefById(any(User.class), eq(BRIEF_ID)))
                .thenThrow(new BriefNotFoundException("Brief not found"));

        mockMvc.perform(get("/api/v1/briefs/{id}", BRIEF_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Brief not found"));

        verify(briefBuilderService).getBriefById(any(User.class), eq(BRIEF_ID));
    }
}
