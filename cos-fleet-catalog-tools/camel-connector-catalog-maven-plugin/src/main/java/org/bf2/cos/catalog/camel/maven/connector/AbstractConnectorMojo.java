package org.bf2.cos.catalog.camel.maven.connector;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.Parameter;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.support.Param;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;

public abstract class AbstractConnectorMojo extends AbstractMojo {
    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;
    @Parameter
    private List<Connector> connectors;

    private boolean injected = false;
    private ExpressionEvaluator expressionEvaluator;

    protected List<Connector> getConnectors() throws MojoExecutionException {
        if (connectors == null) {
            connectors = Collections.emptyList();
        }
        if (!injected) {
            expressionEvaluator = new PluginParameterExpressionEvaluator(
                    Objects.requireNonNull(session, "session required"),
                    new MojoExecution(null));
            try {
                for (Connector connector : connectors) {
                    doInject(connector);
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Unable to inject connectors", e);
            }
            injected = true;
        }
        return connectors;
    }

    private void doInject(Object pojo) throws ExpressionEvaluationException, IllegalAccessException {
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
                                doInject(o);
                            }
                        } else if (value instanceof Map) {
                            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                                doInject(e.getKey());
                                doInject(e.getValue());
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }

    }

}
