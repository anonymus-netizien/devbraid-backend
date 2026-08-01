package com.devbraid.compliance.service;

import com.devbraid.audit.entity.AuditLog;
import com.devbraid.audit.repository.AuditLogRepository;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * SOC 2 / compliance export: streams audit logs and thread summaries into a ZIP
 * with a SHA-256 manifest for audit verification.
 * ponytail: whole-export in memory — audit volumes are small on the MVP; switch to
 * StreamingResponseBody if exports ever exceed tens of MB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceExportService {

    private static final int BATCH = 1000;

    private final AuditLogRepository auditLogRepository;
    private final ChangeThreadRepository changeThreadRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public byte[] exportZip(OffsetDateTime start, OffsetDateTime end, String format) throws IOException {
        boolean json = "json".equalsIgnoreCase(format);
        String ext = json ? "json" : "csv";

        // ponytail: no try/catch in service layer — IOException propagates to
        // GlobalExceptionHandler (400), per the no-try-catch policy.
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            byte[] auditBytes = exportAuditLogs(start, end, json);
            byte[] threadsBytes = exportThreads(start, end, json);

            writeEntry(zos, "audit_logs." + ext, auditBytes);
            writeEntry(zos, "threads_summary." + ext, threadsBytes);
            writeEntry(zos, "manifest.json", manifestJson(auditBytes, threadsBytes));

            zos.finish();
            return baos.toByteArray();
        }
    }

    private byte[] exportAuditLogs(OffsetDateTime start, OffsetDateTime end, boolean json) throws IOException {
        List<AuditLog> logs = auditLogRepository
                .findByDateRange(start, end, PageRequest.of(0, BATCH))
                .getContent();

        if (json) {
            // ponytail: Map.of() rejects nulls; use LinkedHashMap for nullable audit fields
            List<Map<String, Object>> rows = logs.stream().map(l -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", l.getId());
                row.put("userId", l.getUser() != null ? l.getUser().getId() : null);
                row.put("action", l.getAction());
                row.put("entityType", l.getEntityType());
                row.put("entityId", l.getEntityId());
                row.put("details", l.getDetails());
                row.put("ipAddress", l.getIpAddress());
                row.put("userAgent", l.getUserAgent());
                row.put("createdAt", l.getCreatedAt());
                return row;
            }).toList();
            return objectMapper.writeValueAsBytes(rows);
        }

        StringBuilder csv = new StringBuilder("id,userId,action,entityType,entityId,ipAddress,userAgent,createdAt\n");
        for (AuditLog l : logs) {
            csv.append(escapeCsv(l.getId())).append(',')
                    .append(escapeCsv(l.getUser() != null ? l.getUser().getId() : null)).append(',')
                    .append(escapeCsv(l.getAction())).append(',')
                    .append(escapeCsv(l.getEntityType())).append(',')
                    .append(escapeCsv(l.getEntityId())).append(',')
                    .append(escapeCsv(l.getIpAddress())).append(',')
                    .append(escapeCsv(l.getUserAgent())).append(',')
                    .append(escapeCsv(l.getCreatedAt())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportThreads(OffsetDateTime start, OffsetDateTime end, boolean json) throws IOException {
        List<ChangeThread> threads = changeThreadRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, PageRequest.of(0, BATCH))
                .getContent();

        if (json) {
            // ponytail: Map.of() rejects nulls; use LinkedHashMap for nullable thread fields
            List<Map<String, Object>> rows = threads.stream().map(t -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", t.getId());
                row.put("repositoryFullName", t.getRepositoryFullName());
                row.put("headBranch", t.getHeadBranch());
                row.put("baseBranch", t.getBaseBranch());
                row.put("title", t.getTitle());
                row.put("status", t.getStatus() != null ? t.getStatus().name() : null);
                row.put("riskLevel", t.getRiskLevel() != null ? t.getRiskLevel().name() : null);
                row.put("createdAt", t.getCreatedAt());
                return row;
            }).toList();
            return objectMapper.writeValueAsBytes(rows);
        }

        StringBuilder csv = new StringBuilder("id,repositoryFullName,headBranch,baseBranch,title,status,riskLevel,createdAt\n");
        for (ChangeThread t : threads) {
            csv.append(escapeCsv(t.getId())).append(',')
                    .append(escapeCsv(t.getRepositoryFullName())).append(',')
                    .append(escapeCsv(t.getHeadBranch())).append(',')
                    .append(escapeCsv(t.getBaseBranch())).append(',')
                    .append(escapeCsv(t.getTitle())).append(',')
                    .append(escapeCsv(t.getStatus() != null ? t.getStatus().name() : null)).append(',')
                    .append(escapeCsv(t.getRiskLevel() != null ? t.getRiskLevel().name() : null)).append(',')
                    .append(escapeCsv(t.getCreatedAt())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] manifestJson(byte[] auditBytes, byte[] threadsBytes) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        manifest.put("entries", List.of(
                Map.of("file", "audit_logs", "sha256", SecurityUtils.sha256Hex(auditBytes)),
                Map.of("file", "threads_summary", "sha256", SecurityUtils.sha256Hex(threadsBytes))
        ));
        return objectMapper.writeValueAsBytes(manifest);
    }

    private void writeEntry(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(bytes);
        zos.closeEntry();
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString().replace("\"", "\"\"");
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s + "\"" : s;
    }
}
