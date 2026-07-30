package com.devbraid.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic audit logging via AOP.
 * Place on service methods to log actions to the audit_logs table.
 * <p>
 * Example usage:
 *
 * @AuditAction(action = "LOGIN", entityType = "USER")
 * public LoginResponse login(String email, String password) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {
    String action();

    String entityType() default "";

    String entityIdParam() default "";
}
