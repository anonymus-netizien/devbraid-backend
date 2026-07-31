package com.devbraid.ai.fallback;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose failures fall back to a same-argument fallback method on the same bean.
 * Used to keep graceful degradation OUT of service layers (no try/catch in services policy).
 * The {@link AiFallbackAspect} intercepts annotated methods and invokes the fallback on failure.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FallbackMethod {
    /**
     * Name of the fallback method on the same bean, with identical parameter types.
     */
    String method();
}
