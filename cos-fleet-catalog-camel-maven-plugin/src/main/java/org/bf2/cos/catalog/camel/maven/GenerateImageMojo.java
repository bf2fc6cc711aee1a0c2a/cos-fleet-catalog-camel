package org.bf2.cos.catalog.camel.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.model.AppDependency;
import io.quarkus.bootstrap.model.PathsCollection;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.util.IoUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
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
import org.bf2.cos.catalog.camel.maven.suport.KameletsCatalog;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.getClassLoader;

/**
 * Builds the Quarkus application.
 */
@Mojo(name = "generate-image", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateImageMojo extends AbstractMojo {

    private static final String PACKAGE_TYPE_PROP = "quarkus.package.type";
    private static final String NATIVE_PROFILE_NAME = "native";
    private static final String NATIVE_PACKAGE_TYPE = "native";

    @Requirement(role = RepositorySystem.class, optional = false)
    protected RepositorySystem repoSystem;

    @Requirement(role = RemoteRepositoryManager.class, optional = false)
    protected RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;
    @Component
    private MavenProjectHelper projectHelper;
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     */
    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true, required = true)
    private List<RemoteRepository> pluginRepos;
    /**
     * The directory for generated source files.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources")
    private File generatedSourcesDirectory;
    /**
     * Skips the execution of this mojo
     */
    @Parameter(defaultValue = "false", property = "quarkus.build.skip")
    private boolean skip = false;

    /**
     * The list of system properties defined for the plugin.
     */
    @Parameter
    private Map<String, String> systemProperties = Collections.emptyMap();

    @Parameter(defaultValue = "${project.build.directory}")
    private File buildDir;

    @Parameter(defaultValue = "${project.build.finalName}")
    private String finalName;

    /**
     * The project's remote repositories to use for the resolution of artifacts and their dependencies.
     *
     * @parameter default-value="${project.remoteProjectRepositories}"
     * @readonly
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repos;

    private AppArtifactKey projectId;

    /**
     * The properties of the plugin.
     */
    @Parameter(property = "properties", required = false)
    private Map<String, String> properties = new HashMap<>();

    @Parameter(property = "ignoredEntries")
    private String[] ignoredEntries;

    /**
     * The context of the execution of the plugin.
     */
    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    private MojoExecution mojoExecution;

    @Parameter(required = false, property = "appArtifact")
    private String appArtifact;

    @Parameter
    private List<Connector> connectors;

    @Parameter(defaultValue = "${camel-quarkus.version}")
    private String camelQuarkusVersion;

    @Parameter(defaultValue = "${quarkus.native.builder-image}")
    private String builderImage;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!beforeExecute()) {
            return;
        }
        doExecute();
    }

    protected boolean beforeExecute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping Quarkus build");
            return false;
        }
        if (project.getPackaging().equals("pom")) {
            getLog().info("Type of the artifact is POM, skipping build goal");
            return false;
        }
        if (!project.getArtifact().getArtifactHandler().getExtension().equals("jar")) {
            throw new MojoExecutionException(
                    "The project artifact's extension is '" + project.getArtifact().getArtifactHandler().getExtension()
                            + "' while this goal expects it be 'jar'");
        }
        return true;
    }

    protected void doExecute() throws MojoExecutionException {
        try {
            Set<String> propertiesToClear = new HashSet<>();

            // Add the system properties of the plugin to the system properties
            // if and only if they are not already set.
            for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
                String key = entry.getKey();
                if (System.getProperty(key) == null) {
                    System.setProperty(key, entry.getValue());
                    propertiesToClear.add(key);
                }
            }

            // Essentially what this does is to enable the native package type even if a different package type is set
            // in application properties. This is done to preserve what users expect to happen when
            // they execute "mvn package -Dnative" even if quarkus.package.type has been set in application.properties
            if (!System.getProperties().containsKey(PACKAGE_TYPE_PROP)
                    && isNativeProfileEnabled(project)) {
                getLog().warn("Forcing native profile");
                Object packageTypeProp = project.getProperties().get(PACKAGE_TYPE_PROP);
                String packageType = NATIVE_PACKAGE_TYPE;
                if (packageTypeProp != null) {
                    packageType = packageTypeProp.toString();
                }
                System.setProperty(PACKAGE_TYPE_PROP, packageType);
                propertiesToClear.add(PACKAGE_TYPE_PROP);
            }
            if (!propertiesToClear.isEmpty() && session.getRequest().getDegreeOfConcurrency() > 1) {
                getLog().warn("*****************************************************************");
                getLog().warn("* Your build is requesting parallel execution, but the project  *");
                getLog().warn("* relies on System properties at build time which could cause   *");
                getLog().warn("* race condition issues thus unpredictable build results.       *");
                getLog().warn("* Please avoid using System properties or avoid enabling        *");
                getLog().warn("* parallel execution                                            *");
                getLog().warn("*****************************************************************");
            }

            try (CuratedApplication curatedApplication = new AppBootstrapProvider().bootstrapQuarkus().bootstrap()) {
                AugmentAction action = curatedApplication.createAugmentor();
                AugmentResult result = action.createProductionApplication();
                Artifact original = project.getArtifact();

                if (result.getJar() != null) {
                    if (result.getJar().isUberJar()
                            && result.getJar().getOriginalArtifact() != null) {
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
                    if (result.getJar().isUberJar()) {
                        projectHelper.attachArtifact(project, result.getJar().getPath().toFile(),
                                result.getJar().getClassifier());
                    }
                }
            } finally {
                // Clear all the system properties set by the plugin
                propertiesToClear.forEach(System::clearProperty);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build quarkus application", e);
        }
    }

    private boolean isNativeProfileEnabled(MavenProject mavenProject) {
        // gotcha: mavenProject.getActiveProfiles() does not always contain all active profiles (sic!),
        //         but getInjectedProfileIds() does (which has to be "flattened" first)
        Stream<String> activeProfileIds = mavenProject.getInjectedProfileIds().values().stream().flatMap(List<String>::stream);
        if (activeProfileIds.anyMatch(NATIVE_PROFILE_NAME::equalsIgnoreCase)) {
            return true;
        }
        // recurse into parent (if available)
        return Optional.ofNullable(mavenProject.getParent()).map(this::isNativeProfileEnabled).orElse(false);
    }

    private class AppBootstrapProvider {
        private AppArtifact appArtifact;
        private MavenArtifactResolver artifactResolver;
        private QuarkusBootstrap quarkusBootstrap;

        private MavenArtifactResolver artifactResolver()
                throws MojoExecutionException {
            if (artifactResolver != null) {
                return artifactResolver;
            }
            try {
                return artifactResolver = MavenArtifactResolver.builder()
                        .setWorkspaceDiscovery(false)
                        .setRepositorySystem(repoSystem)
                        .setRepositorySystemSession(repoSession)
                        .setRemoteRepositories(repos)
                        .setRemoteRepositoryManager(remoteRepoManager)
                        .build();
            } catch (BootstrapMavenException e) {
                throw new MojoExecutionException("Failed to initialize Quarkus bootstrap Maven artifact resolver", e);
            }
        }

        public QuarkusBootstrap bootstrapQuarkus() throws MojoExecutionException {
            if (quarkusBootstrap != null) {
                return quarkusBootstrap;
            }

            if (camelQuarkusVersion == null) {
                throw new MojoExecutionException("The camelQuarkusVersion should be configured on the plugin");
            }
            // find dependencies
            List<AppDependency> forcedDependencies = new ArrayList();
            final KameletsCatalog catalog = getKameletsCatalog();
            for (Connector connector : connectors) {
                final ObjectNode connectorSpec = catalog.kamelet(
                        connector.getAdapter().getName(),
                        connector.getAdapter().getVersion());
                final ObjectNode kafkaSpec = catalog.kamelet(
                        connector.getKafka().getName(),
                        connector.getKafka().getVersion());
                for (ObjectNode node : Arrays.asList(connectorSpec, kafkaSpec)) {
                    for (JsonNode depNode : node.requiredAt("/spec/dependencies")) {
                        String dep = depNode.asText();
                        if (dep.startsWith("mvn:")) {
                            String[] coords = dep.substring("mvn:".length()).split(":");
                            forcedDependencies
                                    .add(new AppDependency(new AppArtifact(coords[0], coords[1], coords[2]), "compile"));
                        } else if (dep.startsWith("camel:")) {
                            String coord = dep.substring("camel:".length());
                            forcedDependencies
                                    .add(new AppDependency(
                                            new AppArtifact("org.apache.camel.quarkus", "camel-quarkus-" + coord,
                                                    camelQuarkusVersion),
                                            "compile"));
                        } else {
                            throw new MojoExecutionException("Unsupported dependency: " + dep);
                        }
                    }
                }
            }
            for (Artifact artifact : project.getArtifacts()) {
                forcedDependencies
                        .add(new AppDependency(
                                new AppArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()),
                                artifact.getScope()));
            }

            final Properties projectProperties = project.getProperties();
            final Properties effectiveProperties = new Properties();
            // quarkus. properties > ignoredEntries in pom.xml
            if (ignoredEntries != null && ignoredEntries.length > 0) {
                String joinedEntries = String.join(",", ignoredEntries);
                effectiveProperties.setProperty("quarkus.package.user-configured-ignored-entries", joinedEntries);
            }
            for (String name : projectProperties.stringPropertyNames()) {
                if (name.startsWith("quarkus.")) {
                    effectiveProperties.setProperty(name, projectProperties.getProperty(name));
                }
            }

            // Add plugin properties
            effectiveProperties.putAll(properties);
            effectiveProperties.putIfAbsent("quarkus.application.name", project.getArtifactId());
            effectiveProperties.putIfAbsent("quarkus.application.version", project.getVersion());

            QuarkusBootstrap.Builder builder = QuarkusBootstrap.builder()
                    .setAppArtifact(appArtifact())
                    .setManagingProject(managingProject())
                    .setMavenArtifactResolver(artifactResolver())
                    .setIsolateDeployment(true)
                    .setBaseClassLoader(getClass().getClassLoader())
                    .setBuildSystemProperties(effectiveProperties)
                    .setLocalProjectDiscovery(false)
                    .setProjectRoot(project.getBasedir().toPath())
                    .setBaseName(finalName)
                    .setTargetDirectory(buildDir.toPath())
                    .setForcedDependencies(forcedDependencies);

            for (MavenProject project : project.getCollectedProjects()) {
                builder.addLocalArtifact(new AppArtifactKey(project.getGroupId(), project.getArtifactId(), null,
                        project.getArtifact().getArtifactHandler().getExtension()));
            }

            return quarkusBootstrap = builder.build();
        }

        protected AppArtifact managingProject() {
            if (appArtifact == null) {
                return null;
            }
            final Artifact artifact = project.getArtifact();
            return new AppArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                    artifact.getClassifier(), artifact.getArtifactHandler().getExtension(),
                    artifact.getVersion());
        }

        private AppArtifact appArtifact() throws MojoExecutionException {
            return appArtifact == null ? appArtifact = initAppArtifact() : appArtifact;
        }

        private AppArtifact initAppArtifact() throws MojoExecutionException {
            String appArtifactCoords = GenerateImageMojo.this.appArtifact;
            if (appArtifactCoords == null) {
                final Artifact projectArtifact = project.getArtifact();
                final AppArtifact appArtifact = new AppArtifact(projectArtifact.getGroupId(), projectArtifact.getArtifactId(),
                        projectArtifact.getClassifier(), projectArtifact.getArtifactHandler().getExtension(),
                        projectArtifact.getVersion());

                File projectFile = projectArtifact.getFile();
                if (projectFile == null) {
                    projectFile = new File(project.getBuild().getOutputDirectory());
                    if (!projectFile.exists()) {
                        /*
                         * TODO GenerateCodeMojo would fail
                         * if (hasSources(project)) {
                         * throw new MojoExecutionException("Project " + project.getArtifact() + " has not been compiled yet");
                         * }
                         */
                        if (!projectFile.mkdirs()) {
                            throw new MojoExecutionException("Failed to create the output dir " + projectFile);
                        }
                    }
                }
                appArtifact.setPaths(PathsCollection.of(projectFile.toPath()));
                return appArtifact;
            }

            final String[] coordsArr = appArtifactCoords.split(":");
            if (coordsArr.length < 2 || coordsArr.length > 5) {
                throw new MojoExecutionException(
                        "appArtifact expression " + appArtifactCoords
                                + " does not follow format groupId:artifactId:classifier:type:version");
            }
            final String groupId = coordsArr[0];
            final String artifactId = coordsArr[1];
            String classifier = "";
            String type = "jar";
            String version = null;
            if (coordsArr.length == 3) {
                version = coordsArr[2];
            } else if (coordsArr.length > 3) {
                classifier = coordsArr[2] == null ? "" : coordsArr[2];
                type = coordsArr[3] == null ? "jar" : coordsArr[3];
                if (coordsArr.length > 4) {
                    version = coordsArr[4];
                }
            }
            if (version == null) {
                for (Artifact dep : project.getArtifacts()) {
                    if (dep.getArtifactId().equals(artifactId)
                            && dep.getGroupId().equals(groupId)
                            && dep.getClassifier().equals(classifier)
                            && dep.getType().equals(type)) {
                        return new AppArtifact(dep.getGroupId(),
                                dep.getArtifactId(),
                                dep.getClassifier(),
                                dep.getArtifactHandler().getExtension(),
                                dep.getVersion());
                    }
                }
                throw new IllegalStateException("Failed to locate " + appArtifactCoords + " among the project dependencies");
            }

            final AppArtifact appArtifact = new AppArtifact(groupId, artifactId, classifier, type, version);
            try {
                appArtifact.setPath(
                        artifactResolver().resolve(new DefaultArtifact(groupId, artifactId, classifier, type, version))
                                .getArtifact().getFile().toPath());
            } catch (MojoExecutionException e) {
                throw e;
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to resolve " + appArtifact, e);
            }
            return appArtifact;
        }
    }

    private KameletsCatalog getKameletsCatalog() throws MojoExecutionException {
        try {
            return new KameletsCatalog(getClassLoader(project));
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to load Kamelets catalog", e);
        }
    }
}