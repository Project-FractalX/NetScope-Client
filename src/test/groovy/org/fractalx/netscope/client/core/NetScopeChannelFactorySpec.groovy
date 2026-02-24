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

    // ── channelFor(host, port, auth) — P2 ────────────────────────────────────

    def "channelFor(host, port, auth=NONE): returns a non-null channel"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        def auth    = new NetScopeClientConfig.AuthConfig()   // NONE
        when:
        def ch = factory.channelFor("localhost", 9090, auth)
        then:
        ch != null
        cleanup:
        factory.destroy()
    }

    def "channelFor(host, port, auth=NONE): same as channelFor(host, port) — shares the cache entry"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        when:
        def chNoAuth   = factory.channelFor("localhost", 9090)
        def chNoneAuth = factory.channelFor("localhost", 9090, new NetScopeClientConfig.AuthConfig())
        then:
        chNoAuth.is(chNoneAuth)
        cleanup:
        factory.destroy()
    }

    def "channelFor(host, port, auth=API_KEY): returns different channel from NONE on same host:port"() {
        given:
        def factory  = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        def apiKeyAuth = new NetScopeClientConfig.AuthConfig()
        apiKeyAuth.type   = NetScopeClientConfig.AuthType.API_KEY
        apiKeyAuth.apiKey = "secret"
        when:
        def chNone   = factory.channelFor("localhost", 9090)
        def chApiKey = factory.channelFor("localhost", 9090, apiKeyAuth)
        then:
        !chNone.is(chApiKey)
        cleanup:
        factory.destroy()
    }

    def "channelFor(host, port, auth=API_KEY): same key returns same channel (caching)"() {
        given:
        def factory  = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        def auth1 = new NetScopeClientConfig.AuthConfig()
        auth1.type = NetScopeClientConfig.AuthType.API_KEY; auth1.apiKey = "k1"
        def auth2 = new NetScopeClientConfig.AuthConfig()
        auth2.type = NetScopeClientConfig.AuthType.API_KEY; auth2.apiKey = "k1"
        when:
        def ch1 = factory.channelFor("localhost", 9090, auth1)
        def ch2 = factory.channelFor("localhost", 9090, auth2)
        then:
        ch1.is(ch2)
        cleanup:
        factory.destroy()
    }

    def "channelFor(host, port, auth=API_KEY): different keys return different channels"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        def auth1 = new NetScopeClientConfig.AuthConfig()
        auth1.type = NetScopeClientConfig.AuthType.API_KEY; auth1.apiKey = "key-a"
        def auth2 = new NetScopeClientConfig.AuthConfig()
        auth2.type = NetScopeClientConfig.AuthType.API_KEY; auth2.apiKey = "key-b"
        when:
        def ch1 = factory.channelFor("localhost", 9090, auth1)
        def ch2 = factory.channelFor("localhost", 9090, auth2)
        then:
        !ch1.is(ch2)
        cleanup:
        factory.destroy()
    }

    def "channelFor(host, port, auth=OAUTH): returns different channel from NONE on same host:port"() {
        given:
        def factory   = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        def oauthAuth = new NetScopeClientConfig.AuthConfig()
        oauthAuth.type          = NetScopeClientConfig.AuthType.OAUTH
        oauthAuth.tokenProvider = "myTokenBean"
        when:
        def chNone  = factory.channelFor("localhost", 9090)
        def chOauth = factory.channelFor("localhost", 9090, oauthAuth)
        then:
        !chNone.is(chOauth)
        cleanup:
        factory.destroy()
    }

    def "channelFor(host, port, auth=OAUTH): same provider returns same channel (caching)"() {
        given:
        def factory = new NetScopeChannelFactory(new NetScopeClientConfig(), appCtx)
        def mkOauth = { String provider ->
            def a = new NetScopeClientConfig.AuthConfig()
            a.type = NetScopeClientConfig.AuthType.OAUTH; a.tokenProvider = provider; a
        }
        when:
        def ch1 = factory.channelFor("localhost", 9090, mkOauth("bean"))
        def ch2 = factory.channelFor("localhost", 9090, mkOauth("bean"))
        then:
        ch1.is(ch2)
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
