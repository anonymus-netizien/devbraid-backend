package com.devbraid.audit.aspect;

import com.devbraid.audit.annotation.AuditAction;
import com.devbraid.audit.service.AuditService;
import com.devbraid.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final HttpServletRequest request;

    @Around("@annotation(com.devbraid.audit.annotation.AuditAction)")
    public Object auditMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            status = "FAILED";
            errorMessage = ex.getMessage();
            throw ex;
        } finally {
            try {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                Method method = signature.getMethod();
                AuditAction annotation = method.getAnnotation(AuditAction.class);

                String action = annotation.action();
                String entityType = annotation.entityType();

                // Extract entityId from method parameter if specified
                UUID entityId = null;
                if (!annotation.entityIdParam().isEmpty()) {
                    entityId = extractEntityId(joinPoint, signature, annotation.entityIdParam());
                }

                // Build details map
                Map<String, Object> details = new HashMap<>();
                details.put("status", status);
                details.put("duration_ms", System.currentTimeMillis() - startTime);
                details.put("method", method.getName());
                if (errorMessage != null) {
                    details.put("error", errorMessage);
                }

                // Get current user
                User currentUser = getCurrentUser();

                // Log the audit entry
                auditService.log(
                        currentUser,
                        action,
                        entityType,
                        entityId,
                        details,
                        request
                );
            } catch (Exception ex) {
                log.warn("Failed to create audit log entry: {}", ex.getMessage());
            }
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private UUID extractEntityId(ProceedingJoinPoint joinPoint, MethodSignature signature, String paramName) {
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName) && args[i] instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }
}
