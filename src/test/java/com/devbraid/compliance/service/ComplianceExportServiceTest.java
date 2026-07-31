package com.devbraid.compliance.service;

import com.devbraid.audit.entity.AuditLog;
import com.devbraid.audit.repository.AuditLogRepository;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.ThreadStatus;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("ComplianceExportService Unit Tests")
@ExtendWith(MockitoExtension.class)
class ComplianceExportServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ChangeThreadRepository changeThreadRepository;

    private ComplianceExportService service;

    @BeforeEach
    void setUp() {
        // ponytail: plain ObjectMapper can't serialize OffsetDateTime; register JavaTimeModule
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new ComplianceExportService(auditLogRepository, changeThreadRepository, mapper);
    }

    @Test
    @DisplayName("exportZip returns a ZIP with audit_logs, threads_summary, and manifest")
    void exportZip_containsExpectedEntries() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-12-31T23:59:59Z");

        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID())
                .action("LOGIN")
                .entityType("USER")
                .ipAddress("127.0.0.1")
                .createdAt(OffsetDateTime.now())
                .build();
        when(auditLogRepository.findByDateRange(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 1000), 1));

        ChangeThread thread = ChangeThread.builder()
                .id(UUID.randomUUID())
                .repositoryFullName("owner/repo")
                .headBranch("feature")
                .baseBranch("main")
                .title("Test")
                .status(ThreadStatus.READY)
                .build();
        when(changeThreadRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(thread), PageRequest.of(0, 1000), 1));

        byte[] zip = service.exportZip(start, end, "json");

        assertTrue(zip.length > 0);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            boolean sawAudit = false, sawThreads = false, sawManifest = false;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("audit_logs.json")) sawAudit = true;
                if (entry.getName().equals("threads_summary.json")) sawThreads = true;
                if (entry.getName().equals("manifest.json")) sawManifest = true;
            }
            assertTrue(sawAudit, "audit_logs.json present");
            assertTrue(sawThreads, "threads_summary.json present");
            assertTrue(sawManifest, "manifest.json present");
        }
    }
}
