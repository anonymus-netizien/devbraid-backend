package com.devbraid.audit.controller;

import com.devbraid.audit.dto.response.AuditLogResponse;
import com.devbraid.audit.service.AuditService;
import com.devbraid.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AuditController Unit Tests")
@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    private static final UUID USER_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440008");

    private MockMvc mockMvc;

    @Mock
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        AuditController controller = new AuditController(auditService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver()
                )
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit-logs returns paged response")
    void list_returnsPagedAuditLogs() throws Exception {
        AuditLogResponse entry = new AuditLogResponse(
                UUID.randomUUID(), USER_ID, "LOGIN", "USER", null,
                null, "127.0.0.1", "curl", OffsetDateTime.now());
        var page = new PageImpl<AuditLogResponse>(List.of(entry), PageRequest.of(0, 20), 1);

        when(auditService.searchLogs(eq(null), eq(null), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.data.content[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET with userId filter passes filter through to service")
    void list_withUserIdFilter_passesFilter() throws Exception {
        when(auditService.searchLogs(eq(USER_ID), eq(null), any())).thenReturn(
                new PageImpl<AuditLogResponse>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/admin/audit-logs").param("userId", USER_ID.toString()))
                .andExpect(status().isOk());

        verify(auditService).searchLogs(eq(USER_ID), eq(null), any());
    }

    @Test
    @DisplayName("GET with action filter passes filter through to service")
    void list_withActionFilter_passesFilter() throws Exception {
        when(auditService.searchLogs(eq(null), eq("LOGIN"), any())).thenReturn(
                new PageImpl<AuditLogResponse>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/admin/audit-logs").param("action", "LOGIN"))
                .andExpect(status().isOk());

        verify(auditService).searchLogs(eq(null), eq("LOGIN"), any());
    }

    @Test
    @DisplayName("GET with malformed userId returns 400")
    void list_withInvalidUserId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs").param("userId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
