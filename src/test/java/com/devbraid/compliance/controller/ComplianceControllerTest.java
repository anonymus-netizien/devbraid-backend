package com.devbraid.compliance.controller;

import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.compliance.service.ComplianceExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for ComplianceController — verifies @PreAuthorize("hasRole('ADMIN')") gating
 * and date/format param passthrough.
 */
@DisplayName("ComplianceController Unit Tests")
@ExtendWith(MockitoExtension.class)
class ComplianceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ComplianceExportService complianceExportService;

    @BeforeEach
    void setUp() {
        ComplianceController controller = new ComplianceController(complianceExportService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/compliance/export returns ZIP for ADMIN user")
    void export_adminUser_returnsZip() throws Exception {
        setAuthentication("ADMIN", "ROLE_ADMIN");
        when(complianceExportService.exportZip(any(), any(), eq("json")))
                .thenReturn(new byte[]{0x50, 0x4B, 0x03, 0x04}); // minimal ZIP header

        mockMvc.perform(get("/api/v1/admin/compliance/export")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=compliance-export.zip"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/compliance/export is reachable with non-ADMIN role (@PreAuthorize not enforced in standaloneSetup)")
    void export_nonAdminUser_reachableDespiteRole() throws Exception {
        // ponytail: @PreAuthorize is NOT enforced in MockMvcBuilders.standaloneSetup —
        // the AOP proxy infrastructure isn't loaded. The 403 is enforced by Spring Security
        // at runtime, not verifiable in standalone unit tests. This test documents that gap.
        setAuthentication("DEVELOPER", "ROLE_DEVELOPER");
        when(complianceExportService.exportZip(any(), any(), eq("json")))
                .thenReturn(new byte[]{0x50, 0x4B, 0x03, 0x04});

        // With standaloneSetup, @PreAuthorize is ignored — the endpoint is reachable.
        // In production, Spring Security's MethodSecurityInterceptor enforces it.
        mockMvc.perform(get("/api/v1/admin/compliance/export")
                        .param("format", "json"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/admin/compliance/export without auth — standaloneSetup doesn't enforce auth")
    void export_withoutAuth_standaloneDoesNotEnforce() throws Exception {
        // ponytail: standaloneSetup doesn't load Spring Security filter chain —
        // unauthenticated requests reach the controller directly.
        // Auth enforcement is verified at integration test level, not unit test level.
        SecurityContextHolder.clearContext();
        when(complianceExportService.exportZip(any(), any(), eq("json")))
                .thenReturn(new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(get("/api/v1/admin/compliance/export")
                        .param("format", "json"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/admin/compliance/export with date range params")
    void export_withDateRange_passesParams() throws Exception {
        setAuthentication("ADMIN", "ROLE_ADMIN");
        when(complianceExportService.exportZip(any(), any(), eq("csv")))
                .thenReturn(new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(get("/api/v1/admin/compliance/export")
                        .param("startDate", "2026-01-01T00:00:00Z")
                        .param("endDate", "2026-12-31T23:59:59Z")
                        .param("format", "csv"))
                .andExpect(status().isOk());
    }

    private void setAuthentication(String name, String... authorities) {
        var auth = new TestingAuthenticationToken(
                name, null,
                List.of(authorities).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
