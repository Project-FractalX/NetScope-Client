package org.fractalx.netscope.client.proxy;

import org.fractalx.netscope.client.annotation.SetAttribute;
import org.fractalx.netscope.client.config.NetScopeClientConfig;
import org.fractalx.netscope.client.core.NetScopeTemplate;
import org.fractalx.netscope.client.reactive.ReactiveSupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * JDK {@link InvocationHandler} that bridges interface method calls to {@link NetScopeTemplate}.
 *
 * <p>One instance is created per {@link org.fractalx.netscope.client.annotation.NetScopeClient}
 * interface proxy. Method calls are dispatched based on return type:</p>
 * <ul>
 *   <li>{@code T} (concrete) → blocking gRPC call</li>
 *   <li>{@code void} → blocking call, result discarded</li>
 *   <li>{@code Mono<T>} → non-blocking, fires on subscription</li>
 *   <li>{@code Flux<T>} → streaming via {@code InvokeMethodStream} RPC</li>
 *   <li>{@code CompletableFuture<T>} → async wrapper around blocking call</li>
 * </ul>
 */
public class NetScopeInvocationHandler implements InvocationHandler {

    private final NetScopeTemplate template;
    private final String serverName;            // null when host/port are used instead
    private final String host;                  // null when serverName is used
    private final int port;
    private final String remoteBeanName;
    private final ReactiveSupport reactive;
    private final NetScopeClientConfig.AuthConfig inlineAuthConfig; // null when serverName is used

    /** Constructor without inline auth (backward-compatible). */
    public NetScopeInvocationHandler(
            NetScopeTemplate template,
            String serverName,
            String host,
            int port,
            String remoteBeanName,
            ReactiveSupport reactive) {
        this(template, serverName, host, port, remoteBeanName, reactive, null);
    }

    /** Constructor with optional inline auth (used by host/port proxy clients). */
    public NetScopeInvocationHandler(
            NetScopeTemplate template,
            String serverName,
            String host,
            int port,
            String remoteBeanName,
            ReactiveSupport reactive,
            NetScopeClientConfig.AuthConfig inlineAuthConfig) {
        this.template          = template;
        this.serverName        = serverName;
        this.host              = host;
        this.port              = port;
        this.remoteBeanName    = remoteBeanName;
        this.reactive          = reactive;
        this.inlineAuthConfig  = inlineAuthConfig;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Short-circuit standard Object methods to avoid spurious remote calls
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }

        Class<?> returnType = method.getReturnType();
        Type genericReturn  = method.getGenericReturnType();
        Object[] callArgs   = args != null ? args : new Object[0];
        String methodName   = method.getName();

        // P0: extract simple type names from method signature for exact overload resolution
        String[] paramTypeNames = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .toArray(String[]::new);

        NetScopeTemplate.BeanStep bean = resolveServerStep().bean(remoteBeanName);
        if (paramTypeNames.length > 0) {
            bean = bean.withParameterTypes(paramTypeNames);
        }

        // P1a: @SetAttribute — route to SetAttribute RPC instead of InvokeMethod
        SetAttribute setAttr = method.getAnnotation(SetAttribute.class);
        if (setAttr != null) {
            String fieldName = setAttr.value().isBlank() ? methodName : setAttr.value();
            Object newVal    = callArgs.length > 0 ? callArgs[0] : null;
            Class<?> prevType = (returnType == void.class || returnType == Void.class)
                    ? Object.class : returnType;
            return bean.setAttribute(fieldName, newVal, prevType);
        }

        // void / Void — invoke but discard result
        if (returnType == void.class || returnType == Void.class) {
            bean.invoke(methodName, void.class, callArgs);
            return null;
        }

        // Mono<T>
        if (reactive.isMono(returnType)) {
            Class<?> elementType = reactive.extractTypeArgument(genericReturn);
            return bean.invokeMono(methodName, elementType, callArgs);
        }

        // Flux<T>
        if (reactive.isFlux(returnType)) {
            Class<?> elementType = reactive.extractTypeArgument(genericReturn);
            return bean.invokeFlux(methodName, elementType, callArgs);
        }

        // CompletableFuture<T>
        if (CompletableFuture.class.isAssignableFrom(returnType)) {
            Class<?> elementType = reactive.extractTypeArgument(genericReturn);
            final NetScopeTemplate.BeanStep finalBean = bean;
            return CompletableFuture.supplyAsync(() ->
                finalBean.invoke(methodName, elementType, callArgs));
        }

        // Blocking call — preserves generic type info (e.g. List<Foo>)
        return bean.invoke(methodName, genericReturn, callArgs);
    }

    private NetScopeTemplate.ServerStep resolveServerStep() {
        if (host != null && !host.isBlank() && port > 0) {
            return inlineAuthConfig != null
                    ? template.server(host, port, inlineAuthConfig)
                    : template.server(host, port);
        }
        return template.server(serverName);
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "toString" -> "NetScopeClientProxy[server=" +
                (serverName != null ? serverName : host + ":" + port) +
                ", bean=" + remoteBeanName + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals"   -> proxy == args[0];
            default         -> method.invoke(this, args);
        };
    }
}
