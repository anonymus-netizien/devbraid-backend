package com.devbraid.audit.service;

import com.devbraid.audit.dto.response.AuditLogResponse;
import com.devbraid.audit.entity.AuditLog;
import com.devbraid.audit.repository.AuditLogRepository;
import com.devbraid.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an audit event. Append-only — never updates or deletes.
     * Uses REQUIRES_NEW to ensure audit entry persists even if the calling transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String entityType, UUID entityId,
                    Map<String, Object> details, HttpServletRequest request) {
        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .ipAddress(extractClientIp(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit log: action={}, entityType={}, entityId={}", action, entityType, entityId);
    }

    /**
     * Log an audit event without request context (for async/scheduled tasks).
     * Uses REQUIRES_NEW to ensure audit entry persists even if the calling transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String entityType, UUID entityId,
                    Map<String, Object> details) {
        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByUser(UUID userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByAction(String action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByEntity(String entityType, UUID entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByDateRange(OffsetDateTime start, OffsetDateTime end, Pageable pageable) {
        return auditLogRepository.findByDateRange(start, end, pageable);
    }

    /**
     * Admin search over the audit log, composing the existing repo queries by which filters are present.
     * ponytail: if/else over four repo queries instead of a dynamic specification — one user, one query path.
     * Note: userId wins over action when both are supplied — deliberately simple, no combined query.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> searchLogs(UUID userId, String action, Pageable pageable) {
        Page<AuditLog> page;
        if (userId != null) {
            page = auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else if (action != null && !action.isBlank()) {
            page = auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
        } else {
            page = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUser() != null ? log.getUser().getId() : null,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDetails(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt()
        );
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
