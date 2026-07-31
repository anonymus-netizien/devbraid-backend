package com.devbraid.ai.fallback;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * Intercepts methods annotated with {@link FallbackMethod} and, on failure,
 * invokes the named fallback method (same parameter types) on the same bean.
 * <p>
 * This keeps graceful degradation OUT of service layers — the "no try/catch in
 * services" policy is honored because the catch lives in AOP infrastructure.
 * If the fallback method is missing or its invocation fails, the original
 * exception is rethrown unchanged.
 */
@Slf4j
@Aspect
@Component
public class AiFallbackAspect {

    @Around("@annotation(com.devbraid.ai.fallback.FallbackMethod)")
    public Object handleFallback(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        FallbackMethod annotation = signature.getMethod().getAnnotation(FallbackMethod.class);

        try {
            return joinPoint.proceed();
        } catch (Exception ex) {
            log.warn("{} failed ({}), invoking fallback '{}'",
                    signature.toShortString(), ex.getMessage(), annotation.method(), ex);
            try {
                Method fallback = findFallback(signature, annotation.method());
                return fallback.invoke(joinPoint.getTarget(), joinPoint.getArgs());
            } catch (Exception e) {
                // NoSuchMethodException, IllegalAccessException, or the fallback itself failing.
                log.error("Fallback '{}' unavailable or failed — rethrowing original exception", annotation.method(), e);
                throw ex;
            }
        }
    }

    /**
     * Locate the fallback method by name + parameter types. Uses
     * {@link ReflectionUtils#findMethod} (not {@code Class.getMethod}) so non-public
     * fallback methods and CGLIB-proxied targets resolve correctly.
     */
    private Method findFallback(MethodSignature signature, String name) throws NoSuchMethodException {
        Class<?> declaringType = signature.getDeclaringType();
        Method method = ReflectionUtils.findMethod(declaringType, name, signature.getParameterTypes());
        if (method == null) {
            throw new NoSuchMethodException(
                    declaringType.getSimpleName() + "." + name + "(...) — @FallbackMethod target not found");
        }
        method.setAccessible(true);
        return method;
    }
}
