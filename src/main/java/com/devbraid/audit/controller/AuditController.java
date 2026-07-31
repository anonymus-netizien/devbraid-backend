package com.devbraid.audit.controller;

import com.devbraid.audit.dto.response.AuditLogResponse;
import com.devbraid.audit.service.AuditService;
import com.devbraid.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin endpoint for reading the immutable audit log.
 * RBAC is deferred for the single-user MVP — any authenticated user may read
 * audit logs here. When multi-user lands, gate with VIEW_AUDIT_LOG permission.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AuditLogResponse> logs = auditService.searchLogs(userId, action, pageable);
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved", logs));
    }
}
