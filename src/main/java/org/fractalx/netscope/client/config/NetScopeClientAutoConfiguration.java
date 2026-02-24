package org.fractalx.netscope.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fractalx.netscope.client.core.NetScopeChannelFactory;
import org.fractalx.netscope.client.core.NetScopeTemplate;
import org.fractalx.netscope.client.core.NetScopeValueConverter;
import org.fractalx.netscope.client.proxy.NetScopeClientProxyFactory;
import org.fractalx.netscope.client.reactive.ReactiveSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the NetScope client.
 *
 * <p>Registers all infrastructure beans needed to make {@link NetScopeTemplate} and
 * the interface proxy mechanism work. All beans are guarded with
 * {@link ConditionalOnMissingBean} so consuming applications can override any of them.</p>
 *
 * <p>This auto-configuration is activated either by {@code @EnableNetScopeClient}
 * (via its {@code @Import}) or automatically by Spring Boot via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.</p>
 */
@AutoConfiguration
public class NetScopeClientAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeClientAutoConfiguration.class);

    static final String NS_CLIENT_CONFIG = "netscope.internal.client.config";

    public NetScopeClientAutoConfiguration() {
        logger.info("NetScope Client Auto-Configuration initialized");
    }

    // ── Config ────────────────────────────────────────────────────────────────

    @Bean(NS_CLIENT_CONFIG)
    @ConfigurationProperties(prefix = "netscope.client")
    @ConditionalOnMissingBean(name = NS_CLIENT_CONFIG)
    public NetScopeClientConfig netScopeClientConfig() {
        return new NetScopeClientConfig();
    }

    // ── Reactive ──────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public ReactiveSupport netScopeReactiveSupport() {
        return new ReactiveSupport();
    }

    // ── Jackson ───────────────────────────────────────────────────────────────

    /**
     * Provides a fallback {@link ObjectMapper} only when Spring Boot's own Jackson
     * auto-configuration has not created one. In practice this bean only activates
     * in non-Boot test environments.
     */
    @Bean("netScopeObjectMapper")
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper netScopeObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── Core ──────────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public NetScopeValueConverter netScopeValueConverter(ObjectMapper objectMapper) {
        return new NetScopeValueConverter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public NetScopeChannelFactory netScopeChannelFactory(
            @Qualifier(NS_CLIENT_CONFIG) NetScopeClientConfig config,
            ApplicationContext applicationContext) {
        return new NetScopeChannelFactory(config, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public NetScopeTemplate netScopeTemplate(
            NetScopeChannelFactory channelFactory,
            NetScopeValueConverter converter,
            ReactiveSupport reactiveSupport) {
        return new NetScopeTemplate(channelFactory, converter, reactiveSupport);
    }

    // ── Proxy factory ─────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public NetScopeClientProxyFactory netScopeClientProxyFactory(
            NetScopeTemplate template,
            ReactiveSupport reactiveSupport) {
        return new NetScopeClientProxyFactory(template, reactiveSupport);
    }
}
