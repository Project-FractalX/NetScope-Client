package org.fractalx.netscope.client.config

import spock.lang.Specification

class NetScopeClientConfigSpec extends Specification {

    // ── Top-level defaults ────────────────────────────────────────────────────

    def "servers map is empty by default"() {
        expect:
        new NetScopeClientConfig().servers.isEmpty()
    }

    def "setServers replaces the map"() {
        given:
        def config = new NetScopeClientConfig()
        def srv = new NetScopeClientConfig.ServerConfig()
        when:
        config.servers = [myServer: srv]
        then:
        config.servers.size() == 1
        config.servers.myServer.is(srv)
    }

    // ── ServerConfig defaults ─────────────────────────────────────────────────

    def "ServerConfig: default host is 'localhost'"() {
        expect:
        new NetScopeClientConfig.ServerConfig().host == "localhost"
    }

    def "ServerConfig: default port is 9090"() {
        expect:
        new NetScopeClientConfig.ServerConfig().port == 9090
    }

    def "ServerConfig: default auth type is NONE"() {
        expect:
        new NetScopeClientConfig.ServerConfig().auth.type == NetScopeClientConfig.AuthType.NONE
    }

    def "ServerConfig: setHost overrides default"() {
        given:
        def srv = new NetScopeClientConfig.ServerConfig()
        when:
        srv.host = "my.host.internal"
        then:
        srv.host == "my.host.internal"
    }

    def "ServerConfig: setPort overrides default"() {
        given:
        def srv = new NetScopeClientConfig.ServerConfig()
        when:
        srv.port = 50051
        then:
        srv.port == 50051
    }

    def "ServerConfig: setAuth replaces default AuthConfig"() {
        given:
        def srv = new NetScopeClientConfig.ServerConfig()
        def auth = new NetScopeClientConfig.AuthConfig()
        auth.type = NetScopeClientConfig.AuthType.API_KEY
        auth.apiKey = "my-key"
        when:
        srv.auth = auth
        then:
        srv.auth.type == NetScopeClientConfig.AuthType.API_KEY
        srv.auth.apiKey == "my-key"
    }

    // ── AuthConfig defaults ────────────────────────────────────────────────────

    def "AuthConfig: default type is NONE"() {
        expect:
        new NetScopeClientConfig.AuthConfig().type == NetScopeClientConfig.AuthType.NONE
    }

    def "AuthConfig: default apiKey is null"() {
        expect:
        new NetScopeClientConfig.AuthConfig().apiKey == null
    }

    def "AuthConfig: default tokenProvider is null"() {
        expect:
        new NetScopeClientConfig.AuthConfig().tokenProvider == null
    }

    def "AuthConfig: setType to API_KEY"() {
        given:
        def auth = new NetScopeClientConfig.AuthConfig()
        when:
        auth.type = NetScopeClientConfig.AuthType.API_KEY
        then:
        auth.type == NetScopeClientConfig.AuthType.API_KEY
    }

    def "AuthConfig: setType to OAUTH"() {
        given:
        def auth = new NetScopeClientConfig.AuthConfig()
        when:
        auth.type = NetScopeClientConfig.AuthType.OAUTH
        then:
        auth.type == NetScopeClientConfig.AuthType.OAUTH
    }

    def "AuthConfig: setApiKey stores value"() {
        given:
        def auth = new NetScopeClientConfig.AuthConfig()
        when:
        auth.apiKey = "secret-key"
        then:
        auth.apiKey == "secret-key"
    }

    def "AuthConfig: setTokenProvider stores bean name"() {
        given:
        def auth = new NetScopeClientConfig.AuthConfig()
        when:
        auth.tokenProvider = "myTokenBean"
        then:
        auth.tokenProvider == "myTokenBean"
    }

    // ── AuthType enum ─────────────────────────────────────────────────────────

    def "AuthType enum has exactly three values"() {
        expect:
        NetScopeClientConfig.AuthType.values().length == 3
    }

    def "AuthType enum contains NONE, API_KEY, OAUTH"() {
        expect:
        NetScopeClientConfig.AuthType.NONE   != null
        NetScopeClientConfig.AuthType.API_KEY != null
        NetScopeClientConfig.AuthType.OAUTH  != null
    }

    // ── Full config object ────────────────────────────────────────────────────

    def "full config: two servers with different auth types"() {
        given:
        def config = new NetScopeClientConfig()

        def apiKeyAuth = new NetScopeClientConfig.AuthConfig()
        apiKeyAuth.type   = NetScopeClientConfig.AuthType.API_KEY
        apiKeyAuth.apiKey = "key-123"
        def srv1 = new NetScopeClientConfig.ServerConfig()
        srv1.host = "inventory.internal"
        srv1.port = 9090
        srv1.auth = apiKeyAuth

        def oauthAuth = new NetScopeClientConfig.AuthConfig()
        oauthAuth.type          = NetScopeClientConfig.AuthType.OAUTH
        oauthAuth.tokenProvider = "mySupplierBean"
        def srv2 = new NetScopeClientConfig.ServerConfig()
        srv2.host = "auth.internal"
        srv2.port = 9091
        srv2.auth = oauthAuth

        config.servers = [inventory: srv1, auth: srv2]

        expect:
        config.servers.inventory.host         == "inventory.internal"
        config.servers.inventory.auth.type    == NetScopeClientConfig.AuthType.API_KEY
        config.servers.inventory.auth.apiKey  == "key-123"
        config.servers.auth.host              == "auth.internal"
        config.servers.auth.auth.type         == NetScopeClientConfig.AuthType.OAUTH
        config.servers.auth.auth.tokenProvider == "mySupplierBean"
    }
}
