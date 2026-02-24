package org.fractalx.netscope.client.config;

import org.fractalx.netscope.client.proxy.NetScopeClientProxyFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring {@link FactoryBean} that produces a proxy instance for a
 * {@link org.fractalx.netscope.client.annotation.NetScopeClient}-annotated interface.
 *
 * <p>One {@code NetScopeClientFactoryBean} is registered per discovered interface by
 * {@link NetScopeClientRegistrar}. The interface class name is stored as a string to
 * avoid eager class loading during the bean definition phase.</p>
 */
public class NetScopeClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

    private final String interfaceClassName;
    private ApplicationContext applicationContext;

    public NetScopeClientFactoryBean(String interfaceClassName) {
        this.interfaceClassName = interfaceClassName;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        this.applicationContext = ctx;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() throws Exception {
        Class<T> interfaceType = (Class<T>) Class.forName(interfaceClassName);
        NetScopeClientProxyFactory factory = applicationContext.getBean(NetScopeClientProxyFactory.class);
        return factory.createProxy(interfaceType);
    }

    @Override
    public Class<?> getObjectType() {
        try {
            return Class.forName(interfaceClassName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
