package com.devbraid.compliance.controller;

import com.devbraid.compliance.service.ComplianceExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Admin compliance export — streams a ZIP of audit logs + thread summaries
 * with a SHA-256 manifest. Auth-gated (RBAC deferred); date range is optional
 * (defaults to all time) and format is json|csv.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceExportService complianceExportService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "json") String format) {

        OffsetDateTime start = startDate != null ? startDate : OffsetDateTime.MIN;
        OffsetDateTime end = endDate != null ? endDate : OffsetDateTime.now().plusDays(1);
        byte[] zip = complianceExportService.exportZip(start, end, format);

        log.info("Compliance export served ({} bytes, {} format)", zip.length, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=compliance-export.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }
}
