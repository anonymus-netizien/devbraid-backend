package com.devbraid.compliance.controller;

import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.compliance.service.ComplianceExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real {@code @PreAuthorize("hasRole('ADMIN')")} enforcement test. Loads a minimal
 * Spring context with {@code @EnableMethodSecurity} so the controller bean is
 * proxied by Spring Security's method-security interceptor (fully initialized —
 * the hand-rolled ProxyFactory approach NPEs because the authorization manager
 * never receives an ApplicationContext). Verifies ADMIN → 200, DEVELOPER → 403.
 * No web slice jar, no DB, no extra dependencies.
 */
@SpringJUnitConfig(classes = ComplianceControllerSecurityTest.MethodSecurityTestConfig.class)
@DisplayName("ComplianceController @PreAuthorize Enforcement")
class ComplianceControllerSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        ComplianceExportService complianceExportService() {
            return mock(ComplianceExportService.class);
        }

        @Bean
        ComplianceController complianceController(ComplianceExportService service) {
            return new ComplianceController(service);
        }
    }

    @Autowired
    private ComplianceController complianceController;

    @Autowired
    private ComplianceExportService complianceExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(complianceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN can export the compliance ZIP")
    void admin_export_ok() throws Exception {
        when(complianceExportService.exportZip(any(), any(), any())).thenReturn(new byte[]{0x50, 0x4B});
        mockMvc.perform(get("/api/v1/admin/compliance/export"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    @DisplayName("DEVELOPER is forbidden from compliance export")
    void developer_export_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/compliance/export"))
                .andExpect(status().isForbidden());
    }
}
