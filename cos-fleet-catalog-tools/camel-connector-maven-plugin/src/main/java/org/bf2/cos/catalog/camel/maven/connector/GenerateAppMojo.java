package org.bf2.cos.catalog.camel.maven.connector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorDependency;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorIndex;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorManifest;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorSupport;
import org.bf2.cos.catalog.camel.maven.connector.support.KameletsCatalog;
import org.bf2.cos.catalog.camel.maven.connector.support.MojoSupport;

import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.bootstrap.model.AppDependency;
import io.quarkus.maven.BuildMojo;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.runtime.LaunchMode;

import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.JSON_MAPPER;

/**
 * Builds the Quarkus application.
 */
@Mojo(name = "generate-app", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateAppMojo extends BuildMojo {

    @Parameter(defaultValue = "false", property = "cos.app.skip")
    private boolean skip = false;

    @Parameter(defaultValue = "${project.artifactId}", property = "cos.connector.type")
    private String type;
    @Parameter(defaultValue = "${project.version}", property = "cos.connector.version")
    private String version;
    @Parameter(defaultValue = "0", property = "cos.connector.initial-revision")
    private int initialRevision;
    @Parameter(defaultValue = "cos-connector", property = "cos.connector.container.image-prefix")
    private String containerImagePrefix;
    @Parameter(defaultValue = "${project.artifactId}", property = "cos.connector.container.registry")
    private String containerImageRegistry;
    @Parameter(defaultValue = "${project.artifactId}", property = "cos.connector.container.organization")
    private String containerImageOrg;
    @Parameter(property = "cos.connector.container.additional-tags")
    private String containerImageAdditionalTags;
    @Parameter(property = "cos.connector.container.tag", required = true)
    private String containerImageTag;

    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/connectors")
    private File definitionPathLocal;
    @Parameter(defaultValue = "${cos.connector.catalog.root}/${cos.catalog.name}")
    private File definitionPath;
    @Parameter(defaultValue = "${cos.connector.catalog.root}")
    private File indexPath;
    @Parameter(defaultValue = "${cos.catalog.name}")
    private String catalogName;

    @Parameter(defaultValue = "${camel-quarkus.version}")
    private String camelQuarkusVersion;

    @Parameter
    private Connector defaults;
    @Parameter
    private List<Connector> connectors;
    @Parameter
    private List<String> bannedDependencies;

    ConnectorManifest manifest;
    ConnectorManifest manifestLocal;
    String manifestId;
    Path manifestLocalFile;
    Path indexFile;
    ConnectorIndex index;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping App build");
            return;
        }

        try {
            this.manifestId = type.replace("-", "_");
            this.indexFile = indexPath.toPath().resolve("connectors.json");
            this.manifestLocalFile = definitionPathLocal.toPath().resolve(this.manifestId + ".json");

            if (!Files.exists(manifestLocalFile)) {
                getLog().warn("Skipping App build as the definition file " + manifestLocalFile.getFileName() + " is missing");
                return;
            }

            this.manifestLocal = JSON_MAPPER.readValue(manifestLocalFile.toFile(), ConnectorManifest.class);
            this.index = MojoSupport.load(indexFile, ConnectorIndex.class, ConnectorIndex::new);
            this.manifest = index.getConnectors().get(this.manifestId);

            if (manifest != null && manifest.getRevision() >= this.manifestLocal.getRevision()) {
                getLog().info(
                        "Skipping App build (ref. revision:"
                                + this.manifest.getRevision()
                                + ", local revision: "
                                + this.manifestLocal.getRevision()
                                + ")");

                return;
            }

            Set<String> propertiesToClear = new HashSet<>();
            propertiesToClear.add("quarkus.container-image.registry");
            propertiesToClear.add("quarkus.container-image.group");
            propertiesToClear.add("quarkus.container-image.name");
            propertiesToClear.add("quarkus.container-image.tag");
            propertiesToClear.add("quarkus.container-image.additional-tags");

            //
            // Sanitize system properties
            //

            if (mavenProject().getProperties() != null) {
                for (String key : mavenProject().getProperties().stringPropertyNames()) {
                    if (propertiesToClear.contains(key)) {
                        getLog().warn("Removing project-property " + key);
                        mavenProject().getProperties().remove(key);
                    }
                }
            }

            //
            // Set container image related properties
            //

            // TODO: this should be derived from the manifest
            System.setProperty("quarkus.container-image.registry", this.containerImageRegistry);
            System.setProperty("quarkus.container-image.group", this.containerImageOrg);
            System.setProperty("quarkus.container-image.name", this.containerImagePrefix + "-" + type);
            System.setProperty("quarkus.container-image.tag", this.containerImageTag);

            if (containerImageAdditionalTags != null) {
                System.setProperty("quarkus.container-image.additional-tags", containerImageAdditionalTags);
            }

            getLog().info("App info:");

            for (String key : System.getProperties().stringPropertyNames()) {
                if (key.startsWith("quarkus.container-image.")) {
                    getLog().info("  " + key + ": " + System.getProperties().getProperty(key));
                }
            }

            super.execute();

            Files.createDirectories(definitionPath.toPath());

            this.index.getConnectors().put(this.manifestId, this.manifestLocal);

            for (String type : this.manifestLocal.getTypes()) {
                Path src = definitionPathLocal.toPath().resolve(type + ".json");
                Path dst = definitionPath.toPath().resolve(type + ".json");

                getLog().info("Copy connector definition " + src + " to " + dst);

                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }

            getLog().info("Writing connector index to: " + this.indexFile);

            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(this.indexFile),
                    this.index);

            getLog().info("Cleaning up connectors");

            cleanup();

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build quarkus application", e);
        }
    }

    private void cleanup()
            throws MojoExecutionException, MojoFailureException {

        List<Path> definitions = new ArrayList<>();

        index.getConnectors().forEach((k, v) -> {
            for (String type : v.getTypes()) {
                definitions.add(indexPath.toPath().resolve(v.getCatalog()).resolve(type + ".json"));
            }
        });

        try (Stream<Path> files = Files.walk(indexPath.toPath())) {
            for (Path file : files.collect(Collectors.toList())) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                if (file.getFileName().toString().equals("connectors.json")) {
                    continue;
                }

                if (!definitions.contains(file)) {
                    getLog().warn("Deleting " + file + " as it does not match any known connector in this module");
                    Files.delete(file);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    @Override
    protected List<Dependency> forcedDependencies(LaunchMode mode) {
        final Set<ConnectorDependency> connectorsDependecies = new HashSet<>();
        final Set<ConnectorDependency> projectDependencies = new HashSet<>();

        try {
            KameletsCatalog catalog = KameletsCatalog.get(mavenProject(), getLog());

            for (Connector connector : MojoSupport.inject(mavenSession(), defaults, connectors)) {
                ConnectorSupport.dependencies(catalog, connector, camelQuarkusVersion)
                        .stream()
                        .filter(cd -> !isBanned(cd))
                        .forEach(connectorsDependecies::add);
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

        if (bannedDependencies != null) {
            getLog().info("Banned dependencies:");
            bannedDependencies.stream()
                    .distinct()
                    .sorted()
                    .forEach(d -> getLog().info("- " + d));
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

    protected boolean isBanned(ConnectorDependency dependency) {
        return bannedDependencies != null && bannedDependencies.contains(dependency.groupId + ":" + dependency.artifactiId);
    }
}