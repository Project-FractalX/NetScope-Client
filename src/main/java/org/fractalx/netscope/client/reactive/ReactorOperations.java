package org.fractalx.netscope.client.reactive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * The ONLY class in this library that directly imports Project Reactor types.
 *
 * <p>This class is intentionally kept package-private and is only instantiated
 * from {@link ReactiveSupport} after confirming that Reactor is on the classpath.
 * This pattern ensures that classes without Reactor on the runtime classpath will
 * not encounter {@code NoClassDefFoundError} when loading other library classes.</p>
 */
class ReactorOperations {

    /**
     * Wraps a {@link Callable} in a cold {@code Mono}.
     * The callable is executed on each subscription.
     */
    @SuppressWarnings("unchecked")
    static <T> Object mono(Callable<T> callable) {
        return Mono.fromCallable(callable);
    }

    /**
     * Creates a cold {@code Flux} whose emissions are driven by the given {@code sinkConsumer}.
     * The consumer receives a {@link NetScopeFluxSink} adapter that bridges to the real
     * {@code FluxSink}, keeping gRPC streaming logic free of direct Reactor imports.
     */
    @SuppressWarnings("unchecked")
    static <T> Object flux(Consumer<NetScopeFluxSink<T>> sinkConsumer) {
        return Flux.create((reactor.core.publisher.FluxSink<T> reactiveSink) ->
            sinkConsumer.accept(new NetScopeFluxSink<>() {
                @Override public void next(T value)     { reactiveSink.next(value); }
                @Override public void complete()         { reactiveSink.complete(); }
                @Override public void error(Throwable t) { reactiveSink.error(t); }
            })
        );
    }
}
