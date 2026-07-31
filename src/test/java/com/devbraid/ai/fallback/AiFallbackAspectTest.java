package com.devbraid.ai.fallback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiFallbackAspect Unit Tests")
class AiFallbackAspectTest {

    private SampleTarget proxied() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleTarget());
        factory.addAspect(new AiFallbackAspect());
        return factory.getProxy();
    }

    @Test
    @DisplayName("failure invokes the named fallback method")
    void failure_invokesFallback() {
        assertThat(proxied().risky("x")).isEqualTo("fallback:x");
    }

    @Test
    @DisplayName("missing fallback method rethrows the original exception")
    void missingFallback_rethrowsOriginal() {
        assertThatThrownBy(proxied()::noFallback)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    static class SampleTarget {

        @FallbackMethod(method = "fallback")
        String risky(String input) {
            throw new IllegalStateException("boom");
        }

        String fallback(String input) {
            return "fallback:" + input;
        }

        @FallbackMethod(method = "missing")
        String noFallback() {
            throw new IllegalStateException("boom");
        }
    }
}
