package org.fractalx.netscope.client.core;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.fractalx.netscope.client.config.NetScopeClientConfig;
import org.fractalx.netscope.client.exception.NetScopeClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Creates and caches {@link ManagedChannel} instances per server.
 *
 * <p>Channels are cached by server name (for YAML-configured servers) or
 * by {@code "host:port"} key (for inline-configured clients). The factory
 * implements {@link DisposableBean} to gracefully shut down all channels
 * when the Spring application context closes.</p>
 */
public class NetScopeChannelFactory implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeChannelFactory.class);

    private final NetScopeClientConfig config;
    private final ApplicationContext applicationContext;
    private final ConcurrentHashMap<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public NetScopeChannelFactory(NetScopeClientConfig config, ApplicationContext applicationContext) {
        this.config = config;
        this.applicationContext = applicationContext;
    }

    /**
     * Returns a cached channel for a named server defined in {@code netscope.client.servers}.
     *
     * @throws NetScopeClientException if the server name is not found in configuration
     */
    public ManagedChannel channelFor(String serverName) {
        NetScopeClientConfig.ServerConfig serverConfig = config.getServers().get(serverName);
        if (serverConfig == null) {
            throw new NetScopeClientException(
                "No NetScope server configured with name '" + serverName +
                "'. Add it under netscope.client.servers in your application config.");
        }
        return channels.computeIfAbsent(serverName, k ->
            buildChannel(serverConfig.getHost(), serverConfig.getPort(), serverConfig.getAuth()));
    }

    /**
     * Returns a cached channel for an inline host/port with no authentication.
     */
    public ManagedChannel channelFor(String host, int port) {
        return channelFor(host, port, new NetScopeClientConfig.AuthConfig());
    }

    /**
     * Returns a cached channel for an inline host/port with the supplied auth configuration.
     * The cache key incorporates auth details so that two clients pointing at the same
     * host:port but with different credentials each get their own channel.
     */
    public ManagedChannel channelFor(String host, int port, NetScopeClientConfig.AuthConfig auth) {
        String key = buildInlineCacheKey(host, port, auth);
        return channels.computeIfAbsent(key, k -> buildChannel(host, port, auth));
    }

    private String buildInlineCacheKey(String host, int port, NetScopeClientConfig.AuthConfig auth) {
        if (auth == null || auth.getType() == NetScopeClientConfig.AuthType.NONE) {
            return host + ":" + port;
        }
        return switch (auth.getType()) {
            case API_KEY -> host + ":" + port + ":apikey:" + auth.getApiKey();
            case OAUTH   -> host + ":" + port + ":oauth:"  + auth.getTokenProvider();
            default      -> host + ":" + port;
        };
    }

    private ManagedChannel buildChannel(String host, int port, NetScopeClientConfig.AuthConfig auth) {
        logger.info("NetScope client: creating gRPC channel to {}:{}", host, port);
        return NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .intercept(new NetScopeClientInterceptor(auth, applicationContext))
                .build();
    }

    @Override
    public void destroy() {
        logger.info("NetScope client: shutting down {} gRPC channel(s)", channels.size());
        channels.values().forEach(channel -> {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("NetScope client: channel did not terminate in 5s, forcing shutdown");
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        });
        channels.clear();
    }
}
