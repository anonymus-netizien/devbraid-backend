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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    @Around("@annotation(com.devbraid.audit.annotation.AuditAction)")
    public Object auditMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            Object result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            status = "FAILED";
            errorMessage = ex.getMessage();
            throw ex;
        } finally {
            recordAuditEntry(joinPoint, startTime, status, errorMessage);
        }
    }

    private void recordAuditEntry(ProceedingJoinPoint joinPoint, long startTime,
                                  String status, String errorMessage) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AuditAction annotation = method.getAnnotation(AuditAction.class);

            // Build details map with method args and metadata
            Map<String, Object> details = buildDetails(joinPoint, signature, method, status, errorMessage, startTime);

            // Get optional HTTP request
            HttpServletRequest request = getRequest();

            // Log the audit entry
            auditService.log(
                    getCurrentUser(),
                    annotation.action(),
                    annotation.entityType(),
                    extractEntityId(joinPoint, signature, annotation),
                    details,
                    request
            );
        } catch (Exception ex) {
            log.warn("Failed to create audit log entry: {}", ex.getMessage());
        }
    }

    private Map<String, Object> buildDetails(ProceedingJoinPoint joinPoint, MethodSignature signature,
                                             Method method, String status, String errorMessage, long startTime) {
        Map<String, Object> details = new HashMap<>();
        details.put("status", status);
        details.put("duration_ms", System.currentTimeMillis() - startTime);
        details.put("method", method.getDeclaringClass().getSimpleName() + "." + method.getName());

        // Capture sanitized method arguments
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        Map<String, Object> argsMap = new HashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            Object arg = args[i];
            // Sanitize: skip request/response objects, only log primitives, strings, UUIDs
            if (arg != null && isLoggableType(arg)) {
                argsMap.put(paramName, arg.toString());
            }
        }
        if (!argsMap.isEmpty()) {
            details.put("args", argsMap);
        }

        if (errorMessage != null) {
            details.put("error", errorMessage);
        }
        return details;
    }

    private boolean isLoggableType(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean || obj instanceof UUID;
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private UUID extractEntityId(ProceedingJoinPoint joinPoint, MethodSignature signature, AuditAction annotation) {
        if (annotation.entityIdParam().isEmpty()) return null;

        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(annotation.entityIdParam()) && args[i] instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }
}
