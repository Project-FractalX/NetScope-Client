package org.fractalx.netscope.client.proxy;

import org.fractalx.netscope.client.annotation.NetScopeClient;
import org.fractalx.netscope.client.core.NetScopeTemplate;
import org.fractalx.netscope.client.exception.NetScopeClientException;
import org.fractalx.netscope.client.reactive.ReactiveSupport;

import java.lang.reflect.Proxy;

/**
 * Creates JDK dynamic proxies for interfaces annotated with {@link NetScopeClient}.
 *
 * <p>Each proxy delegates all interface method calls to a {@link NetScopeInvocationHandler}
 * that translates them into gRPC calls via {@link NetScopeTemplate}.</p>
 */
public class NetScopeClientProxyFactory {

    private final NetScopeTemplate template;
    private final ReactiveSupport reactiveSupport;

    public NetScopeClientProxyFactory(NetScopeTemplate template, ReactiveSupport reactiveSupport) {
        this.template        = template;
        this.reactiveSupport = reactiveSupport;
    }

    /**
     * Creates a proxy for the given {@link NetScopeClient}-annotated interface.
     *
     * @throws NetScopeClientException if the interface is not annotated or {@code beanName} is empty
     */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> interfaceType) {
        NetScopeClient annotation = interfaceType.getAnnotation(NetScopeClient.class);
        if (annotation == null) {
            throw new NetScopeClientException(
                "Interface " + interfaceType.getName() + " is not annotated with @NetScopeClient");
        }
        if (annotation.beanName() == null || annotation.beanName().isBlank()) {
            throw new NetScopeClientException(
                "@NetScopeClient.beanName() must not be empty on " + interfaceType.getName());
        }

        String serverName = annotation.server().isBlank()   ? null : annotation.server();
        String host       = annotation.host().isBlank()     ? null : annotation.host();
        int    port       = annotation.port();

        if (serverName == null && (host == null || port <= 0)) {
            throw new NetScopeClientException(
                "@NetScopeClient on " + interfaceType.getSimpleName() +
                " must specify either 'server' or both 'host' and 'port'");
        }

        NetScopeInvocationHandler handler = new NetScopeInvocationHandler(
                template, serverName, host, port, annotation.beanName(), reactiveSupport);

        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[]{ interfaceType },
                handler);
    }
}
