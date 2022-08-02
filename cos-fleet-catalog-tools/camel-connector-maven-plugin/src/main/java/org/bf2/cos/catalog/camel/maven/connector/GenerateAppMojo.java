package org.bf2.cos.catalog.camel.maven.connector;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorDependency;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorSupport;
import org.bf2.cos.catalog.camel.maven.connector.support.KameletsCatalog;
import org.bf2.cos.catalog.camel.maven.connector.support.MojoSupport;

import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.bootstrap.model.AppDependency;
import io.quarkus.maven.BuildMojo;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.runtime.LaunchMode;

/**
 * Builds the Quarkus application.
 */
@Mojo(name = "generate-app", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateAppMojo extends BuildMojo {
    @Parameter(defaultValue = "false", property = "cos.app.skip")
    private boolean skip = false;
    @Parameter(defaultValue = "${camel-quarkus.version}")
    private String camelQuarkusVersion;

    @Parameter
    private Connector defaults;
    @Parameter
    private List<Connector> connectors;

    @Override
    protected boolean beforeExecute() throws MojoExecutionException {
        if (!super.beforeExecute()) {
            return false;
        }

        if (camelQuarkusVersion == null) {
            throw new MojoExecutionException("The camelQuarkusVersion should be configured on the plugin");
        }

        return true;
    }

    @Override
    protected List<Dependency> forcedDependencies(LaunchMode mode) {
        final Set<ConnectorDependency> connectorsDependecies = new HashSet<>();
        final Set<ConnectorDependency> projectDependencies = new HashSet<>();

        try {
            KameletsCatalog catalog = KameletsCatalog.get(mavenProject(), getLog());

            for (Connector connector : MojoSupport.inject(mavenSession(), defaults, connectors)) {
                connectorsDependecies.addAll(
                        ConnectorSupport.dependencies(catalog, connector, camelQuarkusVersion));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (org.apache.maven.model.Dependency dependency : mavenProject().getDependencies()) {
            projectDependencies.add(
                    new ConnectorDependency(
                            dependency.getGroupId(),
                            dependency.getArtifactId(),
                            dependency.getVersion()));
        }

        getLog().info("Connectors dependencies:");
        connectorsDependecies.stream()
                .distinct()
                .sorted(Comparator.comparing(ConnectorDependency::toString))
                .forEach(d -> getLog().info("- " + d));

        getLog().info("Project dependencies:");
        projectDependencies.stream()
                .distinct()
                .sorted(Comparator.comparing(ConnectorDependency::toString))
                .forEach(d -> getLog().info("- " + d));

        return Stream.concat(connectorsDependecies.stream(), projectDependencies.stream())
                .distinct()
                .map(d -> new AppArtifact(d.groupId, d.artifactiId, d.version))
                .map(d -> new AppDependency(d, "compile"))
                .collect(Collectors.toList());
    }
}