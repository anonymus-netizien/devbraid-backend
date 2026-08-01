package com.devbraid.ai.fallback;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as having a fallback for graceful degradation.
 * When the annotated method throws, {@link AiFallbackAspect} invokes
 * the named {@code method} on the same bean with the same arguments.
 * <p>
 * This enforces the "no try-catch in services" policy — fallback logic
 * lives in this cross-cutting concern, not in business code.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FallbackMethod {
    /** Name of the fallback method on the same bean. */
    String method();
}
