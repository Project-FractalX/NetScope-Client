package org.fractalx.netscope.client.core;

/**
 * Represents a single remote method invocation within a batch bidirectional stream.
 *
 * <p>Use {@link NetScopeTemplate.BeanStep#invokeBatchStream} to send multiple
 * {@code MethodCall}s over a single persistent gRPC {@code InvokeMethodStream} connection,
 * avoiding the per-call connection overhead for high-frequency scenarios.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * Flux<Integer> results = (Flux<Integer>) netScope
 *     .server("inventory-service")
 *     .bean("InventoryService")
 *     .invokeBatchStream(Integer.class,
 *         MethodCall.of("getStock", "SKU-001"),
 *         MethodCall.of("getStock", "SKU-002"),
 *         MethodCall.of("getStock", "SKU-003"));
 * }</pre>
 *
 * <p>The {@link #beanName()} field is optional; when non-blank it overrides the
 * {@code BeanStep}'s default bean name for that specific invocation, enabling
 * cross-bean calls within a single stream.</p>
 */
public record MethodCall(String beanName, String methodName, Object[] args) {

    /**
     * Creates a {@code MethodCall} that uses the {@code BeanStep}'s default bean name.
     */
    public static MethodCall of(String methodName, Object... args) {
        return new MethodCall(null, methodName, args);
    }

    /**
     * Creates a {@code MethodCall} targeting a specific remote bean name,
     * overriding the {@code BeanStep}'s default bean name.
     *
     * <p>Named {@code onBean} (rather than a second {@code of} overload) to avoid
     * ambiguity when called from Groovy or other JVM languages with two {@code String}
     * arguments.</p>
     */
    public static MethodCall onBean(String beanName, String methodName, Object... args) {
        return new MethodCall(beanName, methodName, args);
    }
}
