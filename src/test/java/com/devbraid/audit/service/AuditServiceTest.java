package com.devbraid.audit.service;

import com.devbraid.audit.entity.AuditLog;
import com.devbraid.audit.repository.AuditLogRepository;
import com.devbraid.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AuditService Unit Tests")
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID ENTITY_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private HttpServletRequest httpRequest;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    @DisplayName("log() saves audit entry with all fields populated")
    void log_WithRequest_SavesAuditEntry() {
        User user = User.builder()
                .id(USER_ID)
                .email("test@example.com")
                .fullName("Test User")
                .passwordHash("hash")
                .build();

        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(user, "LOGIN", "USER", USER_ID,
                Map.of("method", "login"), httpRequest);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getAction()).isEqualTo("LOGIN");
        assertThat(saved.getEntityType()).isEqualTo("USER");
        assertThat(saved.getEntityId()).isEqualTo(USER_ID);
        assertThat(saved.getDetails()).containsEntry("method", "login");
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("log() without request saves audit entry without IP/User-Agent")
    void log_WithoutRequest_SavesAuditEntry() {
        User user = User.builder()
                .id(USER_ID)
                .email("test@example.com")
                .fullName("Test User")
                .passwordHash("hash")
                .build();

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(user, "LOGOUT", "USER", USER_ID,
                Map.of("status", "SUCCESS"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("LOGOUT");
        assertThat(saved.getIpAddress()).isNull();
        assertThat(saved.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("log() handles null user gracefully")
    void log_NullUser_SavesAuditEntry() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(null, "SYSTEM_EVENT", null, null, Map.of());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getUser()).isNull();
        assertThat(captor.getValue().getAction()).isEqualTo("SYSTEM_EVENT");
    }

    @Test
    @DisplayName("getAuditLogsByUser delegates to repository")
    void getAuditLogsByUser_DelegatesToRepository() {
        auditService.getAuditLogsByUser(USER_ID, org.springframework.data.domain.PageRequest.of(0, 10));
        verify(auditLogRepository).findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any());
    }

    @Test
    @DisplayName("getAuditLogsByAction delegates to repository")
    void getAuditLogsByAction_DelegatesToRepository() {
        auditService.getAuditLogsByAction("LOGIN", org.springframework.data.domain.PageRequest.of(0, 10));
        verify(auditLogRepository).findByActionOrderByCreatedAtDesc(eq("LOGIN"), any());
    }
}
