package org.fractalx.netscope.client.core

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.Metadata
import org.fractalx.netscope.client.config.NetScopeClientConfig
import org.fractalx.netscope.client.exception.NetScopeClientException
import org.fractalx.netscope.client.grpc.proto.NetScopeServiceGrpc
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import java.util.function.Supplier

class NetScopeClientInterceptorSpec extends Specification {

    // ── Test helpers ──────────────────────────────────────────────────────────

    static final def METHOD = NetScopeServiceGrpc.getInvokeMethodMethod()

    static final Metadata.Key<String> AUTHORIZATION =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

    static final Metadata.Key<String> X_API_KEY =
        Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER)

    /**
     * Runs the interceptor and captures the headers delivered to the underlying
     * call's start() method.
     */
    Metadata runInterceptor(NetScopeClientInterceptor interceptor) {
        Metadata captured = null
        def delegateCall = Mock(ClientCall) {
            start(_, _) >> { listener, headers -> captured = headers }
        }
        def channel = Mock(Channel) {
            newCall(_, _) >> delegateCall
        }
        def wrapped = interceptor.interceptCall(METHOD, CallOptions.DEFAULT, channel)
        wrapped.start(Mock(ClientCall.Listener), new Metadata())
        return captured
    }

    static NetScopeClientConfig.AuthConfig apiKeyAuth(String key) {
        def auth = new NetScopeClientConfig.AuthConfig()
        auth.type   = NetScopeClientConfig.AuthType.API_KEY
        auth.apiKey = key
        auth
    }

    static NetScopeClientConfig.AuthConfig oauthAuth(String providerName) {
        def auth = new NetScopeClientConfig.AuthConfig()
        auth.type          = NetScopeClientConfig.AuthType.OAUTH
        auth.tokenProvider = providerName
        auth
    }

    static NetScopeClientConfig.AuthConfig noAuth() {
        new NetScopeClientConfig.AuthConfig()   // NONE by default
    }

    // ── NONE auth ─────────────────────────────────────────────────────────────

    def "NONE: no authorization header attached"() {
        given:
        def interceptor = new NetScopeClientInterceptor(noAuth(), null)
        when:
        def headers = runInterceptor(interceptor)
        then:
        headers.get(AUTHORIZATION) == null
        headers.get(X_API_KEY)     == null
    }

    // ── API_KEY auth ──────────────────────────────────────────────────────────

    def "API_KEY: attaches x-api-key header with configured value"() {
        given:
        def interceptor = new NetScopeClientInterceptor(apiKeyAuth("test-api-key"), null)
        when:
        def headers = runInterceptor(interceptor)
        then:
        headers.get(X_API_KEY) == "test-api-key"
    }

    def "API_KEY: does not attach authorization header"() {
        given:
        def interceptor = new NetScopeClientInterceptor(apiKeyAuth("some-key"), null)
        when:
        def headers = runInterceptor(interceptor)
        then:
        headers.get(AUTHORIZATION) == null
    }

    def "API_KEY: blank apiKey throws NetScopeClientException"() {
        given:
        def interceptor = new NetScopeClientInterceptor(apiKeyAuth(""), null)
        when:
        runInterceptor(interceptor)
        then:
        thrown(NetScopeClientException)
    }

    def "API_KEY: null apiKey throws NetScopeClientException"() {
        given:
        def auth = new NetScopeClientConfig.AuthConfig()
        auth.type = NetScopeClientConfig.AuthType.API_KEY
        // apiKey left null
        def interceptor = new NetScopeClientInterceptor(auth, null)
        when:
        runInterceptor(interceptor)
        then:
        thrown(NetScopeClientException)
    }

    // ── OAUTH auth ────────────────────────────────────────────────────────────

    def "OAUTH: attaches 'authorization: Bearer <token>' header"() {
        given:
        def ctx = Mock(ApplicationContext) {
            getBean("tokenBean") >> ({ "my-jwt-token" } as Supplier)
        }
        def interceptor = new NetScopeClientInterceptor(oauthAuth("tokenBean"), ctx)
        when:
        def headers = runInterceptor(interceptor)
        then:
        headers.get(AUTHORIZATION) == "Bearer my-jwt-token"
    }

    def "OAUTH: does not attach x-api-key header"() {
        given:
        def ctx = Mock(ApplicationContext) {
            getBean("tokenBean") >> ({ "tok" } as Supplier)
        }
        def interceptor = new NetScopeClientInterceptor(oauthAuth("tokenBean"), ctx)
        when:
        def headers = runInterceptor(interceptor)
        then:
        headers.get(X_API_KEY) == null
    }

    def "OAUTH: missing tokenProvider name throws NetScopeClientException"() {
        given:
        def auth = new NetScopeClientConfig.AuthConfig()
        auth.type = NetScopeClientConfig.AuthType.OAUTH
        // tokenProvider left null
        def interceptor = new NetScopeClientInterceptor(auth, Mock(ApplicationContext))
        when:
        runInterceptor(interceptor)
        then:
        thrown(NetScopeClientException)
    }

    def "OAUTH: blank tokenProvider name throws NetScopeClientException"() {
        given:
        def interceptor = new NetScopeClientInterceptor(oauthAuth(""), Mock(ApplicationContext))
        when:
        runInterceptor(interceptor)
        then:
        thrown(NetScopeClientException)
    }

    def "OAUTH: null token from supplier throws NetScopeClientException"() {
        given:
        def ctx = Mock(ApplicationContext) {
            getBean("tokenBean") >> ({ null } as Supplier)
        }
        def interceptor = new NetScopeClientInterceptor(oauthAuth("tokenBean"), ctx)
        when:
        runInterceptor(interceptor)
        then:
        thrown(NetScopeClientException)
    }

    def "OAUTH: supplier throws exception wrapped as NetScopeClientException"() {
        given:
        def ctx = Mock(ApplicationContext) {
            getBean("tokenBean") >> ({ throw new RuntimeException("token fetch failed") } as Supplier)
        }
        def interceptor = new NetScopeClientInterceptor(oauthAuth("tokenBean"), ctx)
        when:
        runInterceptor(interceptor)
        then:
        def ex = thrown(NetScopeClientException)
        ex.cause instanceof RuntimeException
        ex.cause.message == "token fetch failed"
    }
}
