package org.bf2.cos.catalog.camel.maven.connector.support;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;

public final class MojoSupport {
    private MojoSupport() {
    }

    public static void inject(ExpressionEvaluator expressionEvaluator, Object pojo)
            throws ExpressionEvaluationException, IllegalAccessException {
        if (pojo != null) {
            Class<?> clazz = pojo.getClass();
            // Do not introspect JDK classes to avoid useless --add-opens requirements
            while (clazz != Object.class && !clazz.getName().startsWith("java.") && !clazz.getName().startsWith("javax.")) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Param p = field.getAnnotation(Param.class);
                    if (p != null) {
                        Object value = field.get(pojo);
                        if (value == null) {
                            if (!p.defaultValue().isEmpty()) {
                                value = expressionEvaluator.evaluate(p.defaultValue());
                                field.set(pojo, value);
                            }
                            if (value == null && p.required()) {
                                throw new IllegalArgumentException("Expected non null value for " + clazz.getSimpleName() + "."
                                        + field.getName() + " field");
                            }
                        } else if (value instanceof Collection) {
                            for (Object o : (Collection<?>) value) {
                                inject(expressionEvaluator, o);
                            }
                        } else if (value instanceof Map) {
                            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                                inject(expressionEvaluator, e.getKey());
                                inject(expressionEvaluator, e.getValue());
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
    }

    public static List<Connector> inject(MavenSession session, Connector defaults, List<Connector> connectors)
            throws MojoExecutionException {

        if (connectors == null) {
            connectors = Collections.emptyList();
        }

        ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(
                Objects.requireNonNull(session, "session required"),
                new MojoExecution(null));

        try {
            MojoSupport.inject(expressionEvaluator, defaults);

            for (Connector connector : connectors) {
                MojoSupport.inject(expressionEvaluator, connector);

                if (defaults != null) {
                    if (connector.getCustomizers() == null) {
                        connector.setCustomizers(defaults.getCustomizers());
                    }
                    if (connector.getCapabilities() == null) {
                        connector.setCapabilities(defaults.getCapabilities());
                    }
                    if (connector.getDataShape() == null) {
                        connector.setDataShape(defaults.getDataShape());
                    }
                    if (connector.getActions() == null) {
                        connector.setActions(defaults.getActions());
                    }
                    if (connector.getChannels() == null) {
                        connector.setChannels(defaults.getChannels());
                    }
                    if (connector.getErrorHandler() == null) {
                        connector.setErrorHandler(defaults.getErrorHandler());
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to inject connectors", e);
        }

        return connectors;
    }
}
