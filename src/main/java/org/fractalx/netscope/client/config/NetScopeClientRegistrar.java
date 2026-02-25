package org.fractalx.netscope.client.config;

import org.fractalx.netscope.client.annotation.EnableNetScopeClient;
import org.fractalx.netscope.client.annotation.NetScopeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scans the configured base packages for interfaces annotated with
 * {@link NetScopeClient} and registers a {@link NetScopeClientFactoryBean}
 * {@link BeanDefinition} for each one.
 *
 * <p>Triggered via {@link EnableNetScopeClient}'s {@code @Import}.</p>
 */
public class NetScopeClientRegistrar
        implements ImportBeanDefinitionRegistrar, EnvironmentAware, ResourceLoaderAware {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeClientRegistrar.class);

    private Environment environment;
    private ResourceLoader resourceLoader;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata importingClassMetadata,
            BeanDefinitionRegistry registry) {

        String[] basePackages = resolveBasePackages(importingClassMetadata);
        logger.debug("NetScope client: scanning packages {} for @NetScopeClient interfaces",
                Arrays.toString(basePackages));

        ClassPathScanningCandidateComponentProvider scanner = createScanner();

        for (String pkg : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(pkg)) {
                String className = candidate.getBeanClassName();
                String beanName  = resolveBeanName(className, candidate);
                logger.debug("NetScope client: registering proxy bean '{}' for {}", beanName, className);

                BeanDefinitionBuilder builder = BeanDefinitionBuilder
                        .genericBeanDefinition(NetScopeClientFactoryBean.class)
                        .addConstructorArgValue(className);

                // Publish the produced type so Spring can resolve it without eager instantiation.
                // We store the Class<?> object (not the String name) so that
                // AbstractBeanFactory.getTypeForFactoryBeanFromAttributes() can return it
                // directly without calling ClassUtils.forName(), which uses the framework
                // classloader and can throw IllegalStateException in Spring Boot fat-jar setups.
                BeanDefinition beanDef = builder.getBeanDefinition();
                try {
                    beanDef.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Class.forName(className));
                } catch (ClassNotFoundException ignored) {
                    // Defensive: should never happen here since the scanner already loaded
                    // the class to read @NetScopeClient metadata. If it does, getObjectType()
                    // still resolves the type at first-use time.
                }

                registry.registerBeanDefinition(beanName, beanDef);
            }
        }
    }

    private ClassPathScanningCandidateComponentProvider createScanner() {
        // useDefaultFilters = false → only include what we explicitly add
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        // Only process interfaces (not abstract classes or concrete classes)
                        return beanDefinition.getMetadata().isInterface();
                    }
                };
        scanner.addIncludeFilter(new AnnotationTypeFilter(NetScopeClient.class));
        scanner.setResourceLoader(resourceLoader);
        return scanner;
    }

    private String[] resolveBasePackages(AnnotationMetadata meta) {
        AnnotationAttributes attrs = AnnotationAttributes.fromMap(
                meta.getAnnotationAttributes(EnableNetScopeClient.class.getName()));

        if (attrs == null) {
            return new String[]{ defaultPackage(meta) };
        }

        String[]   packages      = attrs.getStringArray("basePackages");
        Class<?>[] packageClasses = attrs.getClassArray("basePackageClasses");

        List<String> result = new ArrayList<>(Arrays.asList(packages));
        for (Class<?> clazz : packageClasses) {
            result.add(clazz.getPackage().getName());
        }
        if (result.isEmpty()) {
            result.add(defaultPackage(meta));
        }
        return result.toArray(new String[0]);
    }

    private String defaultPackage(AnnotationMetadata meta) {
        String className = meta.getClassName();
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(0, lastDot) : "";
    }

    private String resolveBeanName(String className, BeanDefinition candidate) {
        // Use @NetScopeClient.value() if set, otherwise decapitalize interface simple name.
        // Use Class.getSimpleName() (not substring-after-dot) so that nested classes
        // (e.g. test fixtures compiled as OuterClass$InnerInterface) resolve correctly.
        try {
            Class<?> clazz = Class.forName(className);
            NetScopeClient annotation = clazz.getAnnotation(NetScopeClient.class);
            if (annotation != null && !annotation.value().isBlank()) {
                return annotation.value();
            }
            String simpleName = clazz.getSimpleName();
            return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
        } catch (ClassNotFoundException ignored) {}

        int lastDot = className.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? className.substring(lastDot + 1) : className;
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
