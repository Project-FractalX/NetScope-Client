package org.fractalx.netscope.client.reactive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Detects Project Reactor at class-load time and provides helpers for type inspection
 * and reactive wrapping — without importing Reactor types directly.
 *
 * <p>All actual Reactor API calls are delegated to {@link ReactorOperations}, which is
 * only instantiated when Reactor is confirmed to be on the classpath. This ensures that
 * applications without Reactor can load this class without errors.</p>
 */
public class ReactiveSupport {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveSupport.class);

    private static final boolean REACTOR_PRESENT;
    private static final Class<?> MONO_CLASS;
    private static final Class<?> FLUX_CLASS;

    static {
        boolean present = false;
        Class<?> mono = null, flux = null;
        try {
            mono    = Class.forName("reactor.core.publisher.Mono");
            flux    = Class.forName("reactor.core.publisher.Flux");
            present = true;
            logger.debug("NetScope client: Project Reactor detected — Mono/Flux return types supported");
        } catch (ClassNotFoundException ignored) {
            logger.debug("NetScope client: Project Reactor not on classpath — reactive return types disabled");
        }
        REACTOR_PRESENT = present;
        MONO_CLASS      = mono;
        FLUX_CLASS      = flux;
    }

    public boolean isReactorPresent() {
        return REACTOR_PRESENT;
    }

    public boolean isMono(Class<?> type) {
        return REACTOR_PRESENT && MONO_CLASS != null && MONO_CLASS.isAssignableFrom(type);
    }

    public boolean isFlux(Class<?> type) {
        return REACTOR_PRESENT && FLUX_CLASS != null && FLUX_CLASS.isAssignableFrom(type);
    }

    /**
     * Extracts the first type argument from a parameterized type.
     * For example, {@code Mono<String>} returns {@code String.class}.
     * Returns {@code Object.class} if the type argument cannot be resolved.
     */
    public Class<?> extractTypeArgument(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> c) return c;
            if (arg instanceof ParameterizedType inner) return (Class<?>) inner.getRawType();
        }
        return Object.class;
    }

    /**
     * Wraps a blocking callable in a cold {@code Mono<T>}.
     * The callable runs on the subscribing thread.
     *
     * @return a {@code Mono<T>} instance (typed as {@code Object} to avoid compile-time Reactor import)
     * @throws IllegalStateException if Reactor is not on the classpath
     */
    @SuppressWarnings("unchecked")
    public <T> Object wrapAsMono(Callable<T> callable) {
        requireReactor("Mono");
        return ReactorOperations.mono(callable);
    }

    /**
     * Creates a cold {@code Flux<T>} driven by the given sink consumer.
     * The consumer receives a {@link NetScopeFluxSink} to emit values, errors, and completion.
     *
     * @return a {@code Flux<T>} instance (typed as {@code Object} to avoid compile-time Reactor import)
     * @throws IllegalStateException if Reactor is not on the classpath
     */
    @SuppressWarnings("unchecked")
    public <T> Object wrapAsFlux(Consumer<NetScopeFluxSink<T>> sinkConsumer) {
        requireReactor("Flux");
        return ReactorOperations.flux(sinkConsumer);
    }

    private void requireReactor(String type) {
        if (!REACTOR_PRESENT) {
            throw new IllegalStateException(
                "Cannot create " + type + ": Project Reactor is not on the classpath. " +
                "Add spring-boot-starter-webflux to your dependencies.");
        }
    }
}
