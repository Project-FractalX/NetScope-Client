package org.fractalx.netscope.client.annotation;

import org.fractalx.netscope.client.config.NetScopeClientAutoConfiguration;
import org.fractalx.netscope.client.config.NetScopeClientRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables NetScope client support for a Spring Boot application.
 *
 * <p>Place this annotation on your {@code @SpringBootApplication} class (or any
 * {@code @Configuration} class) to activate:</p>
 * <ul>
 *   <li>Scanning of {@code basePackages} for interfaces annotated with {@link NetScopeClient}</li>
 *   <li>Auto-configuration of the gRPC channel factory, value converter, and template</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableNetScopeClient(basePackages = "com.myapp.clients")
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({NetScopeClientRegistrar.class, NetScopeClientAutoConfiguration.class})
public @interface EnableNetScopeClient {

    /**
     * Base packages to scan for {@link NetScopeClient} interfaces.
     * Defaults to the package of the annotated class when empty.
     */
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()}.
     * The package of each specified class is used as a scan base package.
     */
    Class<?>[] basePackageClasses() default {};
}
