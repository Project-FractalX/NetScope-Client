package org.fractalx.netscope.client.annotation;

import org.fractalx.netscope.client.config.NetScopeClientConfig;

import java.lang.annotation.*;

/**
 * Marks an interface as a NetScope client proxy.
 *
 * <p>The library generates a Spring bean (JDK dynamic proxy) for each interface
 * annotated with {@code @NetScopeClient}. Method calls on the proxy are transparently
 * translated into gRPC {@code InvokeMethod} calls against the target NetScope server.</p>
 *
 * <h3>Server resolution (in priority order):</h3>
 * <ol>
 *   <li>If {@link #host()} and {@link #port()} are provided, they are used directly.</li>
 *   <li>Otherwise {@link #server()} is resolved against {@code netscope.client.servers.<name>}
 *       in your application configuration.</li>
 * </ol>
 *
 * <h3>Inline auth (host/port clients only):</h3>
 * <p>When using {@link #host()} and {@link #port()} you can attach auth inline via
 * {@link #authType()}, {@link #apiKey()}, and {@link #tokenProvider()}.  Named-server
 * clients always use the auth configured under {@code netscope.client.servers.<name>.auth}.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Named server (auth from YAML config)
 * @NetScopeClient(server = "inventory-service", beanName = "InventoryService")
 * public interface InventoryClient {
 *     int getStock(String productId);
 *     Mono<Integer> getStockAsync(String id);
 *     Flux<Event> streamEvents();
 * }
 *
 * // Inline host/port with API-key auth
 * @NetScopeClient(host = "metrics.internal", port = 9090,
 *                 authType = AuthType.API_KEY, apiKey = "secret",
 *                 beanName = "MetricsService")
 * public interface MetricsClient {
 *     double getCpuUsage();
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NetScopeClient {

    /**
     * Optional Spring bean name for the generated proxy.
     * Defaults to the decapitalized simple name of the annotated interface.
     */
    String value() default "";

    /**
     * Named server from {@code netscope.client.servers.<name>} in application config.
     * Ignored when {@link #host()} and {@link #port()} are provided.
     */
    String server() default "";

    /**
     * Remote Spring bean name on the NetScope server side.
     * Required.
     */
    String beanName();

    /**
     * Inline host override. Takes precedence over {@link #server()} when both {@code host}
     * and {@code port} are specified.
     */
    String host() default "";

    /**
     * Inline port override. Used together with {@link #host()}.
     * Defaults to {@code 0} (unset); must be a positive integer when host is provided.
     */
    int port() default 0;

    // ── Inline auth (only applicable when host + port are used) ──────────────

    /**
     * Authentication type for inline host/port connections.
     * Ignored when {@link #server()} is used (auth comes from YAML config instead).
     */
    NetScopeClientConfig.AuthType authType() default NetScopeClientConfig.AuthType.NONE;

    /**
     * API key value. Used when {@link #authType()} is {@code API_KEY}.
     * Ignored when {@link #server()} is used.
     */
    String apiKey() default "";

    /**
     * Spring bean name of a {@code Supplier<String>} that returns a valid Bearer token.
     * Used when {@link #authType()} is {@code OAUTH}.
     * Ignored when {@link #server()} is used.
     */
    String tokenProvider() default "";
}
