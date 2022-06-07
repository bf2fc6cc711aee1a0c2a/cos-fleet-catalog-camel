package org.bf2.cos.catalog.camel.maven.connector;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.bf2.cos.catalog.camel.maven.connector.support.AppBootstrapProvider;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorIndex;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorManifest;
import org.bf2.cos.catalog.camel.maven.connector.support.MojoSupport;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.util.IoUtils;

import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.JSON_MAPPER;

/**
 * Builds the Quarkus application.
 */
@Mojo(name = "generate-app", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateAppMojo extends AbstractMojo {

    private static final String PACKAGE_TYPE_PROP = "quarkus.package.type";
    private static final String NATIVE_PACKAGE_TYPE = "native";

    @Component
    protected MavenProjectHelper projectHelper;

    @Requirement(role = RepositorySystem.class, optional = false)
    protected RepositorySystem repoSystem;
    @Requirement(role = RemoteRepositoryManager.class, optional = false)
    protected RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;
    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true, required = true)
    private List<RemoteRepository> pluginRepos;
    @Parameter(defaultValue = "${project.build.directory}/generated-sources")
    private File generatedSourcesDirectory;
    @Parameter(defaultValue = "false", property = "cos.app.skip")
    private boolean skip = false;
    @Parameter
    private Map<String, String> systemProperties;
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;
    @Parameter(defaultValue = "${project.build.finalName}")
    protected String finalName;
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repos;
    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    private MojoExecution mojoExecution;
    @Parameter(required = false, property = "appArtifact")
    private String appArtifact;
    @Parameter(defaultValue = "${camel-quarkus.version}")
    private String camelQuarkusVersion;
    @Parameter(defaultValue = "${quarkus.native.builder-image}")
    private String builderImage;
    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;
    @Parameter
    private Connector defaults;
    @Parameter
    private List<Connector> connectors;

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

            if (systemProperties != null) {
                for (String key : systemProperties.keySet()) {
                    if (propertiesToClear.contains(key)) {
                        getLog().warn("Removing system-property " + key);
                        systemProperties.remove(key);
                    }
                }
            }
            if (project.getProperties() != null) {
                for (String key : project.getProperties().stringPropertyNames()) {
                    if (propertiesToClear.contains(key)) {
                        getLog().warn("Removing project-property " + key);
                        project.getProperties().remove(key);
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

            if (systemProperties != null) {
                // Add the system properties of the plugin to the system properties
                // if and only if they are not already set.
                for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
                    String key = entry.getKey();

                    if (System.getProperty(key) == null) {
                        System.setProperty(key, entry.getValue());
                        propertiesToClear.add(key);
                    }
                }
            }

            getLog().info("App info:");

            for (String key : System.getProperties().stringPropertyNames()) {
                if (key.startsWith("quarkus.container-image.")) {
                    getLog().info("  " + key + ": " + System.getProperties().getProperty(key));
                }
            }

            //
            // Build
            //

            try (CuratedApplication curatedApplication = bootstrapApplication().bootstrapQuarkus().bootstrap()) {
                AugmentAction action = curatedApplication.createAugmentor();
                AugmentResult result = action.createProductionApplication();
                Artifact original = project.getArtifact();

                if (result.getJar() != null && result.getJar().isUberJar()) {
                    if (result.getJar().getOriginalArtifact() != null) {
                        final Path standardJar = result.getJar().getOriginalArtifact();

                        if (Files.exists(standardJar)) {
                            final Path renamedOriginal = standardJar.getParent().toAbsolutePath()
                                    .resolve(standardJar.getFileName() + ".original");

                            try {
                                IoUtils.recursiveDelete(renamedOriginal);
                                Files.move(standardJar, renamedOriginal);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }

                            original.setFile(result.getJar().getOriginalArtifact().toFile());
                        }
                    }

                    projectHelper.attachArtifact(
                            project,
                            result.getJar().getPath().toFile(),
                            result.getJar().getClassifier());
                }

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

            } finally {
                // Clear all the system properties set by the plugin
                propertiesToClear.forEach(System::clearProperty);
            }
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

    protected AppBootstrapProvider bootstrapApplication() {
        AppBootstrapProvider provider = new AppBootstrapProvider();
        provider.setAppArtifactCoords(this.appArtifact);
        provider.setBuildDir(this.buildDir);
        provider.setConnectors(this.connectors);
        provider.setDefaults(this.defaults);
        provider.setFinalName(this.finalName);
        provider.setLog(getLog());
        provider.setProject(this.project);
        provider.setCamelQuarkusVersion(this.camelQuarkusVersion);
        provider.setRemoteRepoManager(this.remoteRepoManager);
        provider.setRepoSession(this.repoSession);
        provider.setRepoSystem(this.repoSystem);
        provider.setSession(this.session);

        return provider;
    }
}