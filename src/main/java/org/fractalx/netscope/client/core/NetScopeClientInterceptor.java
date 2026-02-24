package org.fractalx.netscope.client.core;

import io.grpc.*;
import org.fractalx.netscope.client.config.NetScopeClientConfig;
import org.fractalx.netscope.client.exception.NetScopeClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.function.Supplier;

/**
 * gRPC {@link ClientInterceptor} that attaches authentication headers to every outbound call.
 *
 * <p>Supports two auth modes matching the NetScope server's expected headers:</p>
 * <ul>
 *   <li>{@code API_KEY} → {@code x-api-key: <key>}</li>
 *   <li>{@code OAUTH}   → {@code authorization: Bearer <token>} (token fetched from a {@code Supplier<String>} bean)</li>
 *   <li>{@code NONE}    → no headers added</li>
 * </ul>
 *
 * <p>One instance is created per server channel.</p>
 */
public class NetScopeClientInterceptor implements ClientInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeClientInterceptor.class);

    // Must match the keys defined in NetScopeAuthInterceptor on the server side
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    private final NetScopeClientConfig.AuthConfig authConfig;
    private final ApplicationContext applicationContext;

    public NetScopeClientInterceptor(
            NetScopeClientConfig.AuthConfig authConfig,
            ApplicationContext applicationContext) {
        this.authConfig = authConfig;
        this.applicationContext = applicationContext;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                switch (authConfig.getType()) {
                    case API_KEY -> {
                        String key = authConfig.getApiKey();
                        if (key == null || key.isBlank()) {
                            throw new NetScopeClientException(
                                "API_KEY auth type configured but api-key value is empty");
                        }
                        headers.put(API_KEY_HEADER, key);
                        logger.debug("NetScope client: attached x-api-key header");
                    }
                    case OAUTH -> {
                        String token = resolveToken();
                        headers.put(AUTHORIZATION_KEY, "Bearer " + token);
                        logger.debug("NetScope client: attached authorization Bearer header");
                    }
                    case NONE -> logger.debug("NetScope client: no auth headers attached");
                }
                super.start(responseListener, headers);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private String resolveToken() {
        String providerBeanName = authConfig.getTokenProvider();
        if (providerBeanName == null || providerBeanName.isBlank()) {
            throw new NetScopeClientException(
                "OAUTH auth type configured but no token-provider bean name specified");
        }
        try {
            Supplier<String> supplier = (Supplier<String>) applicationContext.getBean(providerBeanName);
            String token = supplier.get();
            if (token == null || token.isBlank()) {
                throw new NetScopeClientException(
                    "Token provider bean '" + providerBeanName + "' returned a null or empty token");
            }
            return token;
        } catch (NetScopeClientException e) {
            throw e;
        } catch (Exception e) {
            throw new NetScopeClientException(
                "Failed to retrieve OAuth token from bean '" + providerBeanName + "'", e);
        }
    }
}
