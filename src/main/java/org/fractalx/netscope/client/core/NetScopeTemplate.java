package org.fractalx.netscope.client.core;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.fractalx.netscope.client.exception.NetScopeClientException;
import org.fractalx.netscope.client.exception.NetScopeRemoteException;
import org.fractalx.netscope.client.grpc.proto.InvokeRequest;
import org.fractalx.netscope.client.grpc.proto.InvokeResponse;
import org.fractalx.netscope.client.grpc.proto.NetScopeServiceGrpc;
import org.fractalx.netscope.client.reactive.NetScopeFluxSink;
import org.fractalx.netscope.client.reactive.ReactiveSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Low-level fluent API for calling remote NetScope beans.
 *
 * <p>Use this directly when you need dynamic/ad-hoc calls without defining a client interface:</p>
 * <pre>{@code
 * @Autowired NetScopeTemplate netScope;
 *
 * // Untyped
 * Object result = netScope.server("inventory-service").bean("InventoryService").invoke("getStock", "SKU-001");
 *
 * // Typed
 * int stock = netScope.server("inventory-service").bean("InventoryService").invoke("getStock", Integer.class, "SKU-001");
 * }</pre>
 *
 * <p>For most use cases, prefer the declarative {@link org.fractalx.netscope.client.annotation.NetScopeClient}
 * interface proxy approach.</p>
 */
public class NetScopeTemplate {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeTemplate.class);

    private final NetScopeChannelFactory channelFactory;
    private final NetScopeValueConverter converter;
    private final ReactiveSupport reactiveSupport;

    public NetScopeTemplate(
            NetScopeChannelFactory channelFactory,
            NetScopeValueConverter converter,
            ReactiveSupport reactiveSupport) {
        this.channelFactory   = channelFactory;
        this.converter        = converter;
        this.reactiveSupport  = reactiveSupport;
    }

    /**
     * Targets a named server from application configuration.
     *
     * @param serverName key in {@code netscope.client.servers}
     */
    public ServerStep server(String serverName) {
        return new ServerStep(channelFactory.channelFor(serverName));
    }

    /**
     * Targets a server by inline host and port.
     * No auth headers are attached for inline-configured servers.
     */
    public ServerStep server(String host, int port) {
        return new ServerStep(channelFactory.channelFor(host, port));
    }

    // ── Fluent step: server selected ─────────────────────────────────────────

    public class ServerStep {
        private final ManagedChannel channel;

        ServerStep(ManagedChannel channel) {
            this.channel = channel;
        }

        /**
         * Selects the remote Spring bean to invoke operations on.
         *
         * @param beanName the Spring bean class name on the server side
         */
        public BeanStep bean(String beanName) {
            return new BeanStep(channel, beanName);
        }
    }

    // ── Fluent step: server + bean selected ───────────────────────────────────

    public class BeanStep {
        private final ManagedChannel channel;
        private final String beanName;

        BeanStep(ManagedChannel channel, String beanName) {
            this.channel  = channel;
            this.beanName = beanName;
        }

        // ── Blocking calls ────────────────────────────────────────────────────

        /**
         * Invokes a remote method and returns the result as {@code Object}.
         */
        public Object invoke(String methodName, Object... args) {
            return invoke(methodName, Object.class, args);
        }

        /**
         * Invokes a remote method and deserializes the result to {@code returnType}.
         */
        public <T> T invoke(String methodName, Class<T> returnType, Object... args) {
            try {
                logger.debug("NetScope: invoking {}.{}()", beanName, methodName);
                InvokeRequest request = buildRequest(methodName, args);
                InvokeResponse response = blockingStub().invokeMethod(request);
                return converter.fromValue(response.getResult(), returnType);
            } catch (StatusRuntimeException e) {
                throw new NetScopeRemoteException(
                    "Remote call failed: " + beanName + "." + methodName + "()", e);
            } catch (NetScopeClientException e) {
                throw e;
            } catch (Exception e) {
                throw new NetScopeClientException(
                    "Error invoking " + beanName + "." + methodName + "()", e);
            }
        }

        /**
         * Invokes a remote method and deserializes the result to a generic {@code returnType}.
         * Use this for parameterized types such as {@code List<Foo>}.
         */
        public Object invoke(String methodName, Type returnType, Object... args) {
            try {
                logger.debug("NetScope: invoking {}.{}()", beanName, methodName);
                InvokeRequest request = buildRequest(methodName, args);
                InvokeResponse response = blockingStub().invokeMethod(request);
                return converter.fromValue(response.getResult(), returnType);
            } catch (StatusRuntimeException e) {
                throw new NetScopeRemoteException(
                    "Remote call failed: " + beanName + "." + methodName + "()", e);
            } catch (NetScopeClientException e) {
                throw e;
            } catch (Exception e) {
                throw new NetScopeClientException(
                    "Error invoking " + beanName + "." + methodName + "()", e);
            }
        }

        // ── Reactive: Mono ────────────────────────────────────────────────────

        /**
         * Wraps a remote method call in a cold {@code Mono<T>}.
         * The gRPC call fires on subscription.
         *
         * @return a {@code Mono<T>} (returned as {@code Object} to avoid direct Reactor import)
         */
        public <T> Object invokeMono(String methodName, Class<T> returnType, Object... args) {
            Callable<T> callable = () -> invoke(methodName, returnType, args);
            return reactiveSupport.wrapAsMono(callable);
        }

        // ── Reactive: Flux (bidirectional streaming) ──────────────────────────

        /**
         * Invokes a remote method via the bidirectional {@code InvokeMethodStream} RPC
         * and returns a cold {@code Flux<T>} that emits each streamed response.
         *
         * @return a {@code Flux<T>} (returned as {@code Object} to avoid direct Reactor import)
         */
        @SuppressWarnings("unchecked")
        public <T> Object invokeFlux(String methodName, Class<T> elementType, Object... args) {
            Consumer<NetScopeFluxSink<T>> sinkConsumer = sink -> {
                try {
                    InvokeRequest request = buildRequest(methodName, args);
                    StreamObserver<InvokeRequest> requestObserver =
                        asyncStub().invokeMethodStream(new StreamObserver<InvokeResponse>() {
                            @Override
                            public void onNext(InvokeResponse response) {
                                try {
                                    T value = converter.fromValue(response.getResult(), elementType);
                                    sink.next(value);
                                } catch (Exception e) {
                                    sink.error(e);
                                }
                            }
                            @Override public void onError(Throwable t)  { sink.error(t); }
                            @Override public void onCompleted()          { sink.complete(); }
                        });
                    requestObserver.onNext(request);
                    requestObserver.onCompleted();
                } catch (Exception e) {
                    sink.error(e);
                }
            };
            return reactiveSupport.wrapAsFlux((Consumer<NetScopeFluxSink<Object>>) (Consumer<?>) sinkConsumer);
        }

        // ── Private helpers ───────────────────────────────────────────────────

        private InvokeRequest buildRequest(String methodName, Object[] args) {
            return InvokeRequest.newBuilder()
                    .setBeanName(beanName)
                    .setMemberName(methodName)
                    .setArguments(converter.toListValue(args))
                    .build();
        }

        private NetScopeServiceGrpc.NetScopeServiceBlockingStub blockingStub() {
            return NetScopeServiceGrpc.newBlockingStub(channel);
        }

        private NetScopeServiceGrpc.NetScopeServiceStub asyncStub() {
            return NetScopeServiceGrpc.newStub(channel);
        }
    }
}
