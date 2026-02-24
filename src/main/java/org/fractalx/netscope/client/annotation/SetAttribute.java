package org.fractalx.netscope.client.annotation;

import java.lang.annotation.*;

/**
 * Marks a method on a {@link NetScopeClient} interface as a remote field write operation.
 *
 * <p>When present, the method is dispatched via the {@code SetAttribute} gRPC RPC instead of
 * the normal {@code InvokeMethod} RPC.  The first method parameter is used as the new field
 * value, and the return type receives the <em>previous</em> value of the field.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @NetScopeClient(server = "inventory-service", beanName = "InventoryService")
 * public interface InventoryClient {
 *
 *     // Reads the 'stockLevel' field via InvokeMethod
 *     int getStockLevel();
 *
 *     // Writes the 'stockLevel' field via SetAttribute; returns the previous value
 *     @SetAttribute("stockLevel")
 *     int updateStockLevel(int newLevel);
 *
 *     // Field name defaults to the Java method name when value() is blank
 *     @SetAttribute
 *     String description(String newDescription);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SetAttribute {

    /**
     * The field name on the remote bean.
     * Defaults to the annotated Java method's name when left blank.
     */
    String value() default "";
}
