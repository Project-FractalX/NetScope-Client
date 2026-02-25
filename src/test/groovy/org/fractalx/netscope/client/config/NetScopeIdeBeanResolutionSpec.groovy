package org.fractalx.netscope.client.config

import org.fractalx.netscope.client.annotation.EnableNetScopeClient
import org.fractalx.netscope.client.annotation.NetScopeClient
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.type.StandardAnnotationMetadata
import org.springframework.mock.env.MockEnvironment
import org.springframework.stereotype.Component
import spock.lang.Specification

/**
 * Verifies the IDE bean-resolution mechanism.
 *
 * IntelliJ resolves {@code @Autowired} injection points by walking the annotation
 * meta-hierarchy.  Because {@code @NetScopeClient} carries {@code @Component} as a
 * meta-annotation, IntelliJ recognises every {@code @NetScopeClient}-annotated interface
 * as a Spring bean and suppresses the "Could not autowire" false positive — the same
 * approach used by Spring Cloud's {@code @FeignClient}.
 *
 * The tests below pin down the runtime invariants that keep bean registration correct:
 *
 *  Invariant 1 — FactoryBean.OBJECT_TYPE_ATTRIBUTE stored as Class<?>
 *    Without this attribute Spring cannot resolve the factory bean's object type with
 *    allowEagerInit=false, so @ConditionalOnMissingBean(XyzClient.class) always thinks
 *    the bean is absent.  Storing it as a String triggers ClassUtils.forName() with the
 *    framework classloader, which fails in fat-jar setups and poisons every unrelated
 *    condition evaluation.
 */
class NetScopeIdeBeanResolutionSpec extends Specification {

    // ── @NetScopeClient interfaces defined in this package ───────────────────

    @NetScopeClient(server = "ide-svc", beanName = "IdeBean")
    interface IdeFixClient {
        String ping()
    }

    @NetScopeClient(value = "ideFixCustom", server = "ide-svc", beanName = "IdeBeanCustom")
    interface IdeFixCustomClient {
        void fire()
    }

    // ── Minimal config class used by integration tests ────────────────────────

    @EnableNetScopeClient(basePackageClasses = [IdeFixClient])
    @Configuration
    static class IdeFixConfig {}

    // ═════════════════════════════════════════════════════════════════════════
    // 1.  @Component meta-annotation — IDE false-positive suppression
    // ═════════════════════════════════════════════════════════════════════════

    def "@NetScopeClient carries @Component as a meta-annotation"() {
        expect:
        NetScopeClient.isAnnotationPresent(Component)
    }

    def "@Component meta-annotation is reachable via annotation hierarchy"() {
        // IntelliJ walks the full annotation meta-hierarchy; this confirms the chain
        // @NetScopeClient -> @Component is present for IDE resolution.
        expect:
        NetScopeClient.getAnnotation(Component) != null
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2.  NetScopeClientRegistrar — FactoryBean.OBJECT_TYPE_ATTRIBUTE
    // ═════════════════════════════════════════════════════════════════════════

    def "registrar sets FactoryBean.OBJECT_TYPE_ATTRIBUTE on every registered bean definition"() {
        given:
        def registry = buildRegistry()
        when:
        runRegistrar(registry)
        then:
        registry.getBeanDefinition("ideFixClient")
                .getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE) != null
        registry.getBeanDefinition("ideFixCustom")
                .getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE) != null
    }

    def "OBJECT_TYPE_ATTRIBUTE is stored as Class<?> not as a String"() {
        // Storing as a String triggers ClassUtils.forName() with the framework classloader,
        // which fails in Spring Boot fat-jar setups and poisons unrelated condition evaluations.
        given:
        def registry = buildRegistry()
        when:
        runRegistrar(registry)
        then:
        def attr = registry.getBeanDefinition("ideFixClient")
                           .getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE)
        attr instanceof Class
        !(attr instanceof String)
    }

    def "OBJECT_TYPE_ATTRIBUTE equals the annotated interface Class"() {
        given:
        def registry = buildRegistry()
        when:
        runRegistrar(registry)
        then:
        registry.getBeanDefinition("ideFixClient")
                .getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE) == IdeFixClient
    }

    def "OBJECT_TYPE_ATTRIBUTE is set correctly for custom-named bean"() {
        given:
        def registry = buildRegistry()
        when:
        runRegistrar(registry)
        then:
        registry.getBeanDefinition("ideFixCustom")
                .getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE) == IdeFixCustomClient
    }

    def "registrar decapitalises the interface simple name as the default bean name"() {
        given:
        def registry = buildRegistry()
        when:
        runRegistrar(registry)
        then:
        registry.containsBeanDefinition("ideFixClient")
    }

    def "registrar uses @NetScopeClient value() attribute as the bean name when set"() {
        given:
        def registry = buildRegistry()
        when:
        runRegistrar(registry)
        then:
        registry.containsBeanDefinition("ideFixCustom")
        !registry.containsBeanDefinition("ideFixCustomClient")
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3.  Integration — Spring context starts cleanly
    // ═════════════════════════════════════════════════════════════════════════

    def "Spring context with @EnableNetScopeClient refreshes without any exception"() {
        given:
        def context = new AnnotationConfigApplicationContext()
        context.register(IdeFixConfig)
        when:
        context.refresh()
        then:
        noExceptionThrown()
        cleanup:
        context.close()
    }

    def "proxy bean ideFixClient is registered and implements the annotated interface"() {
        given:
        def context = buildContext()
        when:
        def bean = context.getBean("ideFixClient")
        then:
        bean instanceof IdeFixClient
        cleanup:
        context.close()
    }

    def "proxy bean ideFixCustom is registered under its custom bean name"() {
        given:
        def context = buildContext()
        when:
        def bean = context.getBean("ideFixCustom")
        then:
        bean instanceof IdeFixCustomClient
        cleanup:
        context.close()
    }

    def "getBean by type resolves IdeFixClient without ambiguity"() {
        given:
        def context = buildContext()
        when:
        def bean = context.getBean(IdeFixClient)
        then:
        bean != null
        cleanup:
        context.close()
    }

    def "NetScopeClientFactoryBean reports correct object type via getObjectType()"() {
        given:
        def context = buildContext()
        when:
        def fb = context.getBeanFactory().getBean("&ideFixClient") as FactoryBean
        then:
        fb.getObjectType() == IdeFixClient
        cleanup:
        context.close()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static DefaultListableBeanFactory buildRegistry() {
        new DefaultListableBeanFactory()
    }

    private static void runRegistrar(DefaultListableBeanFactory registry) {
        def registrar = new NetScopeClientRegistrar()
        registrar.setEnvironment(new MockEnvironment())
        registrar.setResourceLoader(new DefaultResourceLoader())
        registrar.registerBeanDefinitions(
                new StandardAnnotationMetadata(IdeFixConfig),
                registry)
    }

    private static AnnotationConfigApplicationContext buildContext() {
        def context = new AnnotationConfigApplicationContext()
        context.register(IdeFixConfig)
        context.refresh()
        context
    }
}
