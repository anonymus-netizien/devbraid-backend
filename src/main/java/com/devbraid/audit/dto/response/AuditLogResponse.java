package com.devbraid.audit.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only view of an audit log entry for the admin audit-logs API.
 * userId is exposed as a scalar (not a nested entity) — ponytail: flat DTO, no entity graph.
 */
public record AuditLogResponse(
        UUID id,
        UUID userId,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> details,
        String ipAddress,
        String userAgent,
        OffsetDateTime createdAt
) {
}
