package org.fractalx.netscope.client.proxy

import org.fractalx.netscope.client.annotation.NetScopeClient
import org.fractalx.netscope.client.annotation.SetAttribute
import org.fractalx.netscope.client.config.NetScopeClientConfig
import org.fractalx.netscope.client.core.NetScopeChannelFactory
import org.fractalx.netscope.client.core.NetScopeTemplate
import org.fractalx.netscope.client.core.NetScopeValueConverter
import org.fractalx.netscope.client.exception.NetScopeClientException
import org.fractalx.netscope.client.reactive.ReactiveSupport
import spock.lang.Specification

class NetScopeClientProxyFactorySpec extends Specification {

    // ── Valid test interfaces ─────────────────────────────────────────────────

    @NetScopeClient(server = "my-svc", beanName = "GreeterService")
    interface GreeterClient {
        String greet(String name)
    }

    @NetScopeClient(host = "localhost", port = 9090, beanName = "InventoryService")
    interface InventoryClient {
        int getStock(String id)
    }

    @NetScopeClient(server = "svc", beanName = "SomeService", value = "customBeanName")
    interface CustomNamedClient {
        void ping()
    }

    // P2: inline auth interfaces
    @NetScopeClient(host = "localhost", port = 9090,
                    authType = NetScopeClientConfig.AuthType.API_KEY, apiKey = "secret-key",
                    beanName = "SecureService")
    interface ApiKeyInlineClient {
        String getData()
    }

    @NetScopeClient(host = "localhost", port = 9090,
                    authType = NetScopeClientConfig.AuthType.OAUTH, tokenProvider = "myTokenBean",
                    beanName = "OAuthService")
    interface OAuthInlineClient {
        String getData()
    }

    // P1a: @SetAttribute on interface method
    @NetScopeClient(server = "svc", beanName = "MyBean")
    interface FieldWriterClient {
        @SetAttribute("count")
        int setCount(int value)

        @SetAttribute          // defaults field name to method name
        String label(String v)
    }

    // ── Invalid interfaces ────────────────────────────────────────────────────

    @NetScopeClient(server = "svc", beanName = "")
    interface EmptyBeanNameClient {
        void op()
    }

    @NetScopeClient(beanName = "SomeService")  // no server, no host/port
    interface NoServerClient {
        void op()
    }

    interface UnannotatedClient {
        void op()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    def channelFactory = Mock(NetScopeChannelFactory)
    def converter      = Mock(NetScopeValueConverter)
    def reactiveSupport = new ReactiveSupport()
    def template       = new NetScopeTemplate(channelFactory, converter, reactiveSupport)
    def factory        = new NetScopeClientProxyFactory(template, reactiveSupport)

    // ── createProxy — happy paths ─────────────────────────────────────────────

    def "createProxy: returns non-null proxy for server-based client"() {
        when:
        def proxy = factory.createProxy(GreeterClient.class)
        then:
        proxy != null
        proxy instanceof GreeterClient
    }

    def "createProxy: returns non-null proxy for inline host/port client"() {
        when:
        def proxy = factory.createProxy(InventoryClient.class)
        then:
        proxy != null
        proxy instanceof InventoryClient
    }

    def "createProxy: returned proxy implements the annotated interface"() {
        when:
        def proxy = factory.createProxy(GreeterClient.class)
        then:
        GreeterClient.isAssignableFrom(proxy.getClass())
    }

    // ── toString — no remote call ─────────────────────────────────────────────

    def "proxy.toString(): returns descriptive string without making a remote call"() {
        given:
        def proxy = factory.createProxy(GreeterClient.class)
        when:
        def str = proxy.toString()
        then:
        str.contains("my-svc")
        str.contains("GreeterService")
        0 * channelFactory._  // no channel created just for toString
    }

    def "proxy.toString(): inline host/port variant contains host and port"() {
        given:
        def proxy = factory.createProxy(InventoryClient.class)
        when:
        def str = proxy.toString()
        then:
        str.contains("localhost")
        str.contains("9090")
        str.contains("InventoryService")
    }

    // ── hashCode / equals — no remote call ────────────────────────────────────

    def "proxy.hashCode(): returns a value without remote call"() {
        given:
        def proxy = factory.createProxy(GreeterClient.class)
        when:
        def h = proxy.hashCode()
        then:
        noExceptionThrown()
        0 * channelFactory._
    }

    def "proxy.equals(same): returns true without remote call"() {
        given:
        def proxy = factory.createProxy(GreeterClient.class)
        when:
        def eq = proxy.equals(proxy)
        then:
        eq == true
        0 * channelFactory._
    }

    def "proxy.equals(other): returns false for a different proxy"() {
        given:
        def p1 = factory.createProxy(GreeterClient.class)
        def p2 = factory.createProxy(GreeterClient.class)
        expect:
        !p1.equals(p2)
    }

    // ── P2: inline auth — proxy creation ─────────────────────────────────────

    def "createProxy: inline API_KEY client creates proxy without error"() {
        when:
        def proxy = factory.createProxy(ApiKeyInlineClient.class)
        then:
        proxy != null
        proxy instanceof ApiKeyInlineClient
    }

    def "createProxy: inline OAUTH client creates proxy without error"() {
        when:
        def proxy = factory.createProxy(OAuthInlineClient.class)
        then:
        proxy != null
        proxy instanceof OAuthInlineClient
    }

    def "createProxy: inline API_KEY proxy toString contains host and port"() {
        given:
        def proxy = factory.createProxy(ApiKeyInlineClient.class)
        when:
        def str = proxy.toString()
        then:
        str.contains("localhost")
        str.contains("9090")
        str.contains("SecureService")
    }

    // ── P1a: @SetAttribute on interface methods ────────────────────────────────

    def "createProxy: interface with @SetAttribute methods creates proxy without error"() {
        when:
        def proxy = factory.createProxy(FieldWriterClient.class)
        then:
        proxy != null
        proxy instanceof FieldWriterClient
    }

    def "createProxy: @SetAttribute method with explicit value uses that field name"() {
        given:
        def proxy = factory.createProxy(FieldWriterClient.class)
        def method = FieldWriterClient.getMethod("setCount", int.class)
        when:
        def annotation = method.getAnnotation(SetAttribute.class)
        then:
        annotation != null
        annotation.value() == "count"
    }

    def "createProxy: @SetAttribute method with blank value defaults to method name"() {
        given:
        def method = FieldWriterClient.getMethod("label", String.class)
        when:
        def annotation = method.getAnnotation(SetAttribute.class)
        then:
        annotation != null
        annotation.value() == ""     // blank — handler will use method name "label"
    }

    // ── createProxy — validation errors ──────────────────────────────────────

    def "createProxy: missing @NetScopeClient annotation throws NetScopeClientException"() {
        when:
        factory.createProxy(UnannotatedClient.class)
        then:
        def ex = thrown(NetScopeClientException)
        ex.message.contains("UnannotatedClient")
        ex.message.contains("@NetScopeClient")
    }

    def "createProxy: empty beanName throws NetScopeClientException"() {
        when:
        factory.createProxy(EmptyBeanNameClient.class)
        then:
        def ex = thrown(NetScopeClientException)
        ex.message.contains("beanName")
    }

    def "createProxy: no server and no host/port throws NetScopeClientException"() {
        when:
        factory.createProxy(NoServerClient.class)
        then:
        def ex = thrown(NetScopeClientException)
        ex.message.contains("NoServerClient")
    }
}
