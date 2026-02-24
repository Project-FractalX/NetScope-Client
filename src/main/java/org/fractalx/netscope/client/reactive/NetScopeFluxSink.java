package org.fractalx.netscope.client.reactive;

/**
 * A minimal sink abstraction used to bridge gRPC streaming responses into a reactive
 * {@code Flux} without introducing a direct compile-time dependency on Project Reactor
 * in the core library classes.
 *
 * <p>Implementations are created by {@link ReactorOperations} and passed to consumer
 * lambdas that know nothing about the Reactor API.</p>
 */
public interface NetScopeFluxSink<T> {
    void next(T value);
    void complete();
    void error(Throwable t);
}
