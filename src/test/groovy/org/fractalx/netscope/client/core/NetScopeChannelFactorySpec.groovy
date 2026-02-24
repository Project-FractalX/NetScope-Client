package org.fractalx.netscope.client.core

import io.grpc.ManagedChannel
import org.fractalx.netscope.client.config.NetScopeClientConfig
import org.fractalx.netscope.client.exception.NetScopeClientException
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class NetScopeChannelFactorySpec extends Specification {

    def appCtx = Mock(ApplicationContext)

    // ── Helper: build config with one named server ────────────────────────────

    static NetScopeClientConfig configWith(Map<String, ?> servers) {
        def cfg = new NetScopeClientConfig()
        servers.each { name, spec ->
            def srv = new NetScopeClientConfig.ServerConfig()
            srv.host = spec.host ?: "localhost"
            srv.port = spec.port ?: 9090
            cfg.servers[name] = srv
        }
        cfg
    }

    // ── channelFor(serverName) ────────────────────────────────────────────────

    def "channelFor(name): returns a non-null ManagedChannel for configured server"() {
        given:
        def factory = new NetScopeChannelFactory(configWith([svc: [host: "localhost", port: 9090]]), appCtx)
        when:
        def ch = factory.channelFor("svc")
        then:
        ch != null
        ch instanceof ManagedChannel
        cleanup:
        factory.destroy()
    }

    def "channelFor(name): same name returns the same channel instance (caching)"() {
        given:
        def factory = new NetScopeChannelFactory(configWith([svc: [host: "localhost", port: 9090]]), appCtx)
        when:
        def ch1 = factory.channelFor("svc")
        def ch2 = factory.channelFor("svc")
        then:
        ch1.is(ch2)
        cleanup:
        factory.destroy()
    }

    def "channelFor(name): different names return different channel instances"() {
        given:
        def config = configWith([
            svc1: [host: "localhost", port: 9090],
            svc2: [host: "localhost", port: 9091]
        ])
        def factory = new NetScopeChannelFactory(config, appCtx)
        when:
        def ch1 = factory.channelFor("svc1")
        def ch2 = factory.channelFor("svc2")
        then:
        !ch1.is(ch2)
        cleanup:
        factory.destroy()
    }

    def "channelFor(name): unknown server name throws NetScopeClientException"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        when:
        factory.channelFor("unknown-service")
        then:
        def ex = thrown(NetScopeClientException)
        ex.message.contains("unknown-service")
    }

    def "channelFor(name): error message mentions config key path"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        when:
        factory.channelFor("missing")
        then:
        def ex = thrown(NetScopeClientException)
        ex.message.contains("missing")
        ex.message.contains("netscope.client.servers")
    }

    // ── channelFor(host, port) ────────────────────────────────────────────────

    def "channelFor(host, port): returns a non-null ManagedChannel"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        when:
        def ch = factory.channelFor("localhost", 9090)
        then:
        ch != null
        ch instanceof ManagedChannel
        cleanup:
        factory.destroy()
    }

    def "channelFor(host, port): same host and port returns same channel (caching)"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        when:
        def ch1 = factory.channelFor("localhost", 9090)
        def ch2 = factory.channelFor("localhost", 9090)
        then:
        ch1.is(ch2)
        cleanup:
        factory.destroy()
    }

    def "channelFor(host, port): different ports return different channels"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        when:
        def ch1 = factory.channelFor("localhost", 9090)
        def ch2 = factory.channelFor("localhost", 9091)
        then:
        !ch1.is(ch2)
        cleanup:
        factory.destroy()
    }

    // ── destroy ───────────────────────────────────────────────────────────────

    def "destroy(): completes without error when no channels have been created"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        when:
        factory.destroy()
        then:
        noExceptionThrown()
    }

    def "destroy(): shuts down created channels"() {
        given:
        def factory = new NetScopeChannelFactory(configWith([svc: [:]]), appCtx)
        def ch = factory.channelFor("svc")
        when:
        factory.destroy()
        then:
        ch.isShutdown() || ch.isTerminated()
    }

    def "destroy(): can be called multiple times safely"() {
        given:
        def factory = new NetScopeChannelFactory(configWith([svc: [:]]), appCtx)
        factory.channelFor("svc")
        when:
        factory.destroy()
        factory.destroy()   // second call must not throw
        then:
        noExceptionThrown()
    }
}
