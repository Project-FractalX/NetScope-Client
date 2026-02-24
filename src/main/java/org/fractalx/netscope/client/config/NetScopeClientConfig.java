package org.fractalx.netscope.client.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for the NetScope client.
 * Bound to the {@code netscope.client} prefix via {@code @ConfigurationProperties}.
 *
 * <h3>Example YAML</h3>
 * <pre>{@code
 * netscope:
 *   client:
 *     servers:
 *       inventory-service:
 *         host: localhost
 *         port: 9090
 *         auth:
 *           type: API_KEY
 *           api-key: my-secret-key
 *       auth-service:
 *         host: auth.internal
 *         port: 9091
 *         auth:
 *           type: OAUTH
 *           token-provider: myTokenProviderBean
 * }</pre>
 */
public class NetScopeClientConfig {

    private Map<String, ServerConfig> servers = new LinkedHashMap<>();

    public Map<String, ServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerConfig> servers) {
        this.servers = servers;
    }

    // ── ServerConfig ──────────────────────────────────────────────────────────

    public static class ServerConfig {
        private String host = "localhost";
        private int port = 9090;
        private AuthConfig auth = new AuthConfig();

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public AuthConfig getAuth() { return auth; }
        public void setAuth(AuthConfig auth) { this.auth = auth; }
    }

    // ── AuthConfig ────────────────────────────────────────────────────────────

    public static class AuthConfig {
        private AuthType type = AuthType.NONE;

        /** API key value. Used when {@code type = API_KEY}. */
        private String apiKey;

        /**
         * Spring bean name of a {@code Supplier<String>} that returns a valid Bearer token.
         * Used when {@code type = OAUTH}.
         */
        private String tokenProvider;

        public AuthType getType() { return type; }
        public void setType(AuthType type) { this.type = type; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getTokenProvider() { return tokenProvider; }
        public void setTokenProvider(String tokenProvider) { this.tokenProvider = tokenProvider; }
    }

    // ── AuthType ──────────────────────────────────────────────────────────────

    public enum AuthType {
        /** No authentication headers are attached. */
        NONE,
        /** Attaches {@code x-api-key: <key>} header. */
        API_KEY,
        /** Attaches {@code authorization: Bearer <token>} header via a {@code Supplier<String>} bean. */
        OAUTH
    }
}
