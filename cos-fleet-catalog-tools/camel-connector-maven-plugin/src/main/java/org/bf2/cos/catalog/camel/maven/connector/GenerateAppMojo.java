package org.bf2.cos.catalog.camel.maven.connector;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
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
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorDependency;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorSupport;
import org.bf2.cos.catalog.camel.maven.connector.support.KameletsCatalog;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

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

/**
 * Builds the Quarkus application.
 */
@Mojo(name = "generate-app", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateAppMojo extends AbstractConnectorMojo {

    private static final String PACKAGE_TYPE_PROP = "quarkus.package.type";
    private static final String NATIVE_PROFILE_NAME = "native";
    private static final String NATIVE_PACKAGE_TYPE = "native";

    @Requirement(role = RepositorySystem.class, optional = false)
    protected RepositorySystem repoSystem;

    @Requirement(role = RemoteRepositoryManager.class, optional = false)
    protected RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;
    @Component
    protected MavenProjectHelper projectHelper;
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
    @Parameter(defaultValue = "false", property = "cos.app.skip")
    private boolean skip = false;

    /**
     * The list of system properties defined for the plugin.
     */
    @Parameter
    private Map<String, String> systemProperties = Collections.emptyMap();

    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${project.build.finalName}")
    protected String finalName;

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

            final Set<ConnectorDependency> connectorsDependecies = new HashSet<>();
            final Set<ConnectorDependency> projectDependencies = new HashSet<>();

            for (Connector connector : getConnectors()) {
                try {
                    connectorsDependecies.addAll(
                            ConnectorSupport.dependencies(KameletsCatalog.get(project, getLog()), connector,
                                    camelQuarkusVersion));
                } catch (Exception e) {
                    throw new MojoExecutionException(e);
                }
            }

            for (org.apache.maven.model.Dependency dependency : project.getDependencies()) {
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
                    .setForcedDependencies(
                            Stream.concat(connectorsDependecies.stream(), projectDependencies.stream())
                                    .distinct()
                                    .map(d -> new AppArtifact(d.groupId, d.artifactiId, d.version))
                                    .map(d -> new AppDependency(d, "compile"))
                                    .collect(Collectors.toList()));

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
            String appArtifactCoords = GenerateAppMojo.this.appArtifact;
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
}