package org.fractalx.netscope.client.core;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.fractalx.netscope.client.config.NetScopeClientConfig;
import org.fractalx.netscope.client.exception.NetScopeClientException;
import org.fractalx.netscope.client.exception.NetScopeRemoteException;
import org.fractalx.netscope.client.grpc.proto.DocsRequest;
import org.fractalx.netscope.client.grpc.proto.DocsResponse;
import org.fractalx.netscope.client.grpc.proto.InvokeRequest;
import org.fractalx.netscope.client.grpc.proto.InvokeResponse;
import org.fractalx.netscope.client.grpc.proto.NetScopeServiceGrpc;
import org.fractalx.netscope.client.grpc.proto.SetAttributeRequest;
import org.fractalx.netscope.client.grpc.proto.SetAttributeResponse;
import org.fractalx.netscope.client.reactive.NetScopeFluxSink;
import org.fractalx.netscope.client.reactive.ReactiveSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
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

    /**
     * Targets a server by inline host and port with explicit authentication.
     * Use this when a named server entry is not configured in YAML but auth is required.
     */
    public ServerStep server(String host, int port, NetScopeClientConfig.AuthConfig auth) {
        return new ServerStep(channelFactory.channelFor(host, port, auth));
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

        /**
         * Opens a stateful bidirectional {@code InvokeMethodStream} session targeting the
         * given bean.  Subscribe to {@link BidiStreamSession#responseFlux()} first, then
         * call {@link BidiStreamSession#send}/{@link BidiStreamSession#sendTo} for each
         * invocation, and {@link BidiStreamSession#complete()} when done.
         *
         * @param beanName    default remote Spring bean name for the session
         * @param elementType expected Java type of each response
         */
        public <T> BidiStreamSession<T> openBidiStream(String beanName, Class<T> elementType) {
            return new BidiStreamSession<>(channel, beanName, elementType, new String[0]);
        }

        /**
         * Same as {@link #openBidiStream(String, Class)} but also sets parameter type hints
         * for exact overload resolution on every request in the session.
         */
        public <T> BidiStreamSession<T> openBidiStream(
                String beanName, Class<T> elementType, String... parameterTypes) {
            return new BidiStreamSession<>(channel, beanName, elementType, parameterTypes);
        }

        /**
         * Fetches documentation for all registered members on the remote NetScope server.
         * Returns the raw {@link DocsResponse} containing every exposed method and field.
         */
        public DocsResponse getDocs() {
            try {
                return NetScopeServiceGrpc.newBlockingStub(channel)
                        .getDocs(DocsRequest.newBuilder().build());
            } catch (StatusRuntimeException e) {
                throw new NetScopeRemoteException("GetDocs failed", e);
            } catch (Exception e) {
                throw new NetScopeClientException("Error fetching docs", e);
            }
        }
    }

    // ── Fluent step: server + bean selected ───────────────────────────────────

    public class BeanStep {
        private final ManagedChannel channel;
        private final String beanName;
        private final String[] parameterTypes;

        BeanStep(ManagedChannel channel, String beanName) {
            this(channel, beanName, new String[0]);
        }

        BeanStep(ManagedChannel channel, String beanName, String[] parameterTypes) {
            this.channel        = channel;
            this.beanName       = beanName;
            this.parameterTypes = parameterTypes != null ? parameterTypes : new String[0];
        }

        /**
         * Returns a new {@code BeanStep} that will include the given parameter type simple names
         * in every {@code InvokeRequest}, enabling exact overload resolution on the server.
         *
         * <p>Use simple Java type names as the server expects them — e.g. {@code "String"},
         * {@code "int"}, {@code "List"}.  When calling from a declarative proxy interface the
         * types are populated automatically from reflection; this method is for template users
         * who need manual control.</p>
         */
        public BeanStep withParameterTypes(String... types) {
            return new BeanStep(channel, beanName, types);
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

        // ── SetAttribute ─────────────────────────────────────────────────────

        /**
         * Writes a new value to a remote bean field via the {@code SetAttribute} RPC.
         * Returns the field's previous value as {@code Object}.
         */
        public Object setAttribute(String attrName, Object value) {
            return setAttribute(attrName, value, Object.class);
        }

        /**
         * Writes a new value to a remote bean field via the {@code SetAttribute} RPC and
         * deserialises the previous value to {@code returnType}.
         */
        public <T> T setAttribute(String attrName, Object value, Class<T> returnType) {
            try {
                logger.debug("NetScope: setting {}.{}", beanName, attrName);
                SetAttributeRequest req = SetAttributeRequest.newBuilder()
                        .setBeanName(beanName)
                        .setAttributeName(attrName)
                        .setValue(converter.toValue(value))
                        .build();
                SetAttributeResponse resp = blockingStub().setAttribute(req);
                return converter.fromValue(resp.getPreviousValue(), returnType);
            } catch (StatusRuntimeException e) {
                throw new NetScopeRemoteException(
                    "SetAttribute failed: " + beanName + "." + attrName, e);
            } catch (NetScopeClientException e) {
                throw e;
            } catch (Exception e) {
                throw new NetScopeClientException(
                    "Error setting " + beanName + "." + attrName, e);
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

        // ── Bidirectional streaming: batch ────────────────────────────────────

        /**
         * Sends multiple method invocations over <em>one</em> persistent
         * {@code InvokeMethodStream} gRPC connection and returns a cold {@code Flux<T>}
         * that emits each response in order.
         *
         * <p>Use {@link MethodCall#of(String, Object...)} to build the call list.
         * Each {@link MethodCall} may optionally override the default bean name for
         * cross-bean invocations within the same stream.</p>
         *
         * @return a {@code Flux<T>} (returned as {@code Object} to avoid compile-time Reactor import)
         */
        @SuppressWarnings("unchecked")
        public <T> Object invokeBatchStream(Class<T> elementType, MethodCall... calls) {
            Consumer<NetScopeFluxSink<T>> sinkConsumer = sink -> {
                try {
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
                    for (MethodCall call : calls) {
                        InvokeRequest req = buildRequest(call.methodName(), call.args());
                        // Override bean name per-call when the caller explicitly provided one
                        if (call.beanName() != null && !call.beanName().isBlank()
                                && !call.beanName().equals(beanName)) {
                            req = req.toBuilder().setBeanName(call.beanName()).build();
                        }
                        requestObserver.onNext(req);
                    }
                    requestObserver.onCompleted();
                } catch (Exception e) {
                    sink.error(e);
                }
            };
            return reactiveSupport.wrapAsFlux(
                    (Consumer<NetScopeFluxSink<Object>>) (Consumer<?>) sinkConsumer);
        }

        // ── Private helpers ───────────────────────────────────────────────────

        private InvokeRequest buildRequest(String methodName, Object[] args) {
            InvokeRequest.Builder builder = InvokeRequest.newBuilder()
                    .setBeanName(beanName)
                    .setMemberName(methodName)
                    .setArguments(converter.toListValue(args));
            if (parameterTypes.length > 0) {
                builder.addAllParameterTypes(Arrays.asList(parameterTypes));
            }
            return builder.build();
        }

        private NetScopeServiceGrpc.NetScopeServiceBlockingStub blockingStub() {
            return NetScopeServiceGrpc.newBlockingStub(channel);
        }

        private NetScopeServiceGrpc.NetScopeServiceStub asyncStub() {
            return NetScopeServiceGrpc.newStub(channel);
        }
    }

    // ── BidiStreamSession ─────────────────────────────────────────────────────

    /**
     * Stateful bidirectional streaming session. Obtained via
     * {@link ServerStep#openBidiStream(String, Class)}.
     *
     * @param <T> the expected element type of each response
     */
    public class BidiStreamSession<T> {

        private final StreamObserver<InvokeRequest> requestObserver;
        private final String defaultBeanName;
        private final String[] parameterTypes;

        BidiStreamSession(
                ManagedChannel channel,
                String defaultBeanName,
                Class<T> elementType,
                String[] parameterTypes) {
            this.defaultBeanName = defaultBeanName;
            this.parameterTypes  = parameterTypes != null ? parameterTypes : new String[0];

            // Capture sink lazily so the Flux can be subscribed before sends start
            // We use a simple pass-through observer; responses are emitted via a shared sink.
            // Reactor is required here — gated by wrapAsFlux.
            this.requestObserver = NetScopeServiceGrpc.newStub(channel)
                    .invokeMethodStream(new StreamObserver<InvokeResponse>() {
                        @Override public void onNext(InvokeResponse r) {
                            try {
                                sinkRef[0].next(converter.fromValue(r.getResult(), elementType));
                            } catch (Exception e) {
                                sinkRef[0].error(e);
                            }
                        }
                        @Override public void onError(Throwable t) {
                            if (sinkRef[0] != null) sinkRef[0].error(t);
                        }
                        @Override public void onCompleted() {
                            if (sinkRef[0] != null) sinkRef[0].complete();
                        }
                    });
        }

        @SuppressWarnings("unchecked")
        private final NetScopeFluxSink<T>[] sinkRef = new NetScopeFluxSink[1];

        /**
         * Returns a cold {@code Flux<T>} that emits every response from this stream session.
         * Subscribe before calling {@link #send}.
         *
         * @return a {@code Flux<T>} (as {@code Object} to avoid compile-time Reactor import)
         */
        @SuppressWarnings("unchecked")
        public Object responseFlux() {
            Consumer<NetScopeFluxSink<T>> sinkConsumer = sink -> sinkRef[0] = sink;
            return reactiveSupport.wrapAsFlux(
                    (Consumer<NetScopeFluxSink<Object>>) (Consumer<?>) sinkConsumer);
        }

        /**
         * Sends a single method invocation on this stream using the session's default bean name.
         */
        public BidiStreamSession<T> send(String methodName, Object... args) {
            return sendTo(defaultBeanName, methodName, args);
        }

        /**
         * Sends a single method invocation on this stream, overriding the bean name for this
         * specific call.  Named {@code sendTo} (rather than a second {@code send} overload) to
         * avoid JVM-language dispatch ambiguity when both arguments are {@code String}.
         */
        public BidiStreamSession<T> sendTo(String beanName, String methodName, Object... args) {
            InvokeRequest.Builder builder = InvokeRequest.newBuilder()
                    .setBeanName(beanName)
                    .setMemberName(methodName)
                    .setArguments(converter.toListValue(args));
            if (parameterTypes.length > 0) {
                builder.addAllParameterTypes(Arrays.asList(parameterTypes));
            }
            requestObserver.onNext(builder.build());
            return this;
        }

        /**
         * Signals the end of the request stream to the server.
         * The server will process any remaining requests and then complete the response stream.
         */
        public void complete() {
            requestObserver.onCompleted();
        }
    }
}
